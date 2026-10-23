package io.metis.service;
import io.metis.repository.Store;
import io.metis.integration.*;
import io.metis.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
@Service
public class IngestionService {
 private final Store db;private final AssetService assets;private final MarketDataProvider provider;private final ProviderBudget budget;private final MlClient ml;private final ObjectMapper json;
 public final int cacheHours,retries;public final boolean liveEnabled,allowFixtures;
 public IngestionService(Store db,AssetService assets,MarketDataProvider provider,ProviderBudget budget,MlClient ml,ObjectMapper json,@Value("${metis.market-cache-hours:12}") int cacheHours,@Value("${metis.provider-retries:0}") int retries,@Value("${metis.live-ingestion-enabled:false}") boolean liveEnabled,@Value("${metis.allow-fixtures:false}") boolean allowFixtures) {
  this.db=db;this.assets=assets;this.provider=provider;this.budget=budget;this.ml=ml;this.json=json;this.cacheHours=Math.max(1,cacheHours);this.retries=Math.clamp(retries,0,1);this.liveEnabled=liveEnabled;this.allowFixtures=allowFixtures;
 }
 public Map<String,Object> protection() {return Map.of("fixture_mode",allowFixtures,"live_enabled",liveEnabled,"key_configured",provider.configured(),"max_per_minute",budget.minuteLimit(),"max_per_day",budget.dayLimit(),"min_interval_seconds",budget.minimumInterval(),"max_per_job",budget.jobLimit(),"retry_limit",retries,"cache_hours",cacheHours,"actual_requests",db.scalar("select count(*) from provider_requests",Long.class));}
 public Map<String,Object> plan(List<Long> ids,LocalDate from,LocalDate to) {
  if(ids.isEmpty()||ids.size()>20||to.isBefore(from)||from.isBefore(to.minusYears(5))) throw new ApiException(400,"INGESTION_PLAN","Choose 1–20 assets and at most five years.");
  var items=new ArrayList<Map<String,Object>>();int requests=0;
  for(long id:new LinkedHashSet<>(ids)) {
   var a=assets.get(id);long count=db.scalar("select count(*) from historical_prices where asset_id=? and trading_date between ? and ?",Long.class,id,from,to);
   long fresh=db.scalar("select count(*) from ingestion_runs where asset_id=? and provider='Alpha Vantage' and status='SUCCESS' and completed_at>now()-(? * interval '1 hour')",Long.class,id,cacheHours);
   // Compact endpoint only covers its latest 100 observations. Never promise arbitrary historic backfill.
   var dates=db.rows("select distinct trading_date from historical_prices where asset_id=? and trading_date between ? and ? order by trading_date",id,from,to).stream().map(r->r.get("trading_date")).toList();
   var gaps=ml.post("/data/plan",Map.of("start",from,"end",to,"existing",dates));
   boolean needed=((Number)gaps.get("compact_missing_sessions")).intValue()>0&&fresh==0;requests+=needed?1:0;
   var item=new LinkedHashMap<String,Object>(Map.of("asset_id",id,"symbol",a.symbol,"from",from,"to",to,"local_rows",count,"remote_needed",needed,"endpoint","TIME_SERIES_DAILY / compact (latest 100 sessions only)","message",count>0?"Local history retained; import additional local data for longer training history.":"Requested range may exceed compact coverage; local import is recommended."));item.putAll(gaps);items.add(item);
  }
  return Map.of("dry_run",true,"estimated_requests",requests,"items",items,"protection",protection(),"external_requests_made",0);
 }
 @Transactional public Map<String,Object> ingest(long id) {
  assets.get(id);
  if(!liveEnabled) throw new ApiException(409,"LIVE_INGESTION_DISABLED","Live ingestion is disabled. Use the zero-request plan or import local data. Explicit authorization is required before enabling it.");
  if(!provider.configured()) throw new ApiException(503,"PROVIDER_NOT_CONFIGURED","Provider credential is not configured. Stored data remains available.");
  boolean locked=Boolean.TRUE.equals(db.scalar("select pg_try_advisory_xact_lock(714203)",Boolean.class));
  if(!locked) throw new ApiException(429,"INGESTION_BUSY","An ingestion is already running.");
  long recent=db.scalar("select count(*) from ingestion_runs where asset_id=? and provider='Alpha Vantage' and status='SUCCESS' and completed_at>now()-(? * interval '1 hour')",Long.class,id,cacheHours);
  if(recent>0) return Map.of("status","CACHED","records_inserted",0);
  long run=db.insert("insert into ingestion_runs(asset_id,provider,status) values(?,?,'RUNNING') returning id",id,provider.name());
  int attempted=0;
  while(true) {
   long reservation;
   try {reservation=budget.reserve();}catch(ApiException e) {return failed(run,e,attempted);}
   attempted++;
   try {
    var bars=provider.fetch(assets.get(id).symbol);
    budget.finish(reservation,"SUCCESS");
    var rows=bars.stream().map(b->Map.<String,Object>of("trading_date",b.date(),"open",b.open(),"high",b.high(),"low",b.low(),"close",b.close(),"volume",b.volume())).toList();
    return save(id,provider.name(),rows,run,attempted);
   } catch(ApiException e) {
    budget.finish(reservation,e.code);
    if(!e.code.equals("PROVIDER_NETWORK")||attempted>retries||attempted>=budget.jobLimit()) return failed(run,e,attempted);
    try {Thread.sleep(budget.minimumInterval()*1000L);}catch(InterruptedException interrupted) {Thread.currentThread().interrupt();return failed(run,new ApiException(503,"INTERRUPTED","Ingestion interrupted."),attempted);}
   }
  }
 }
 private Map<String,Object> failed(long run,ApiException e,int attempted) {
  db.update("update ingestion_runs set status='FAILED',completed_at=now(),error_message=?,request_count=? where id=?",e.getMessage(),attempted,run);
  return Map.of("status","FAILED","records_inserted",0,"request_count",attempted,"code",e.code,"message",e.getMessage());
 }
 @Transactional public Map<String,Object> importLocal(long id,List<Map<String,Object>> rows,boolean fixture) {
  assets.get(id);if(rows.isEmpty()||rows.size()>5000) throw new ApiException(400,"DATA_LIMIT","Import 1–5000 OHLCV records.");
  if(fixture&&!allowFixtures) throw new ApiException(403,"FIXTURE_DISABLED","Fixture data is permitted only in an isolated test environment.");
  String source=fixture?"TEST_FIXTURE":"Local CSV";
  long run=db.insert("insert into ingestion_runs(asset_id,provider,status) values(?,?,'RUNNING') returning id",id,source);
  return save(id,source,rows,run,0);
 }
 @SuppressWarnings("unchecked") private Map<String,Object> save(long id,String source,List<? extends Map<String,Object>> rows,long run,int attempted) {
  var a=assets.get(id);var result=ml.post("/data/validate",Map.of("asset_id",id,"symbol",a.symbol,"provider",source,"development_only",source.equals("TEST_FIXTURE"),"bars",rows));
  var accepted=(List<Map<String,Object>>)result.get("bars");var quality=(Map<String,Object>)result.get("quality");int inserted=0;
  for(var b:accepted) inserted+=db.update("insert into historical_prices(asset_id,trading_date,open,high,low,close,volume,provider) values(?,?::date,?::numeric,?::numeric,?::numeric,?::numeric,?,?) on conflict(asset_id,trading_date,provider) do nothing",id,b.get("trading_date"),b.get("open"),b.get("high"),b.get("low"),b.get("close"),b.get("volume"),source);
  int skipped=accepted.size()-inserted+((Number)quality.get("skipped")).intValue();int rejected=((Number)quality.get("rejected")).intValue();
  try {db.update("update ingestion_runs set status=?,completed_at=now(),records_inserted=?,records_skipped=?,records_rejected=?,request_count=?,quality_report=?::jsonb where id=?",accepted.isEmpty()?"FAILED":"SUCCESS",inserted,skipped,rejected,attempted,json.writeValueAsString(quality),run);}catch(com.fasterxml.jackson.core.JsonProcessingException e) {throw new IllegalStateException("Quality report serialization failed");}
  return Map.of("status",accepted.isEmpty()?"FAILED":"SUCCESS","records_inserted",inserted,"records_updated",0,"records_skipped",skipped,"records_rejected",rejected,"request_count",attempted,"quality",quality);
 }
}
