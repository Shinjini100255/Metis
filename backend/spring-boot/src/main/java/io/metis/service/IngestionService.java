package io.metis.service;
import io.metis.repository.Store;
import io.metis.integration.MarketDataProvider;
import io.metis.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.util.*;
@Service
public class IngestionService {
 private final Store db;private final AssetService assets;private final MarketDataProvider provider;private final int cacheHours,dailyLimit,interval,retries;
 public IngestionService(Store db,AssetService assets,MarketDataProvider provider,@Value("${metis.market-cache-hours}") int cacheHours,@Value("${metis.market-daily-limit}") int dailyLimit,@Value("${metis.market-min-interval-seconds}") int interval,@Value("${metis.provider-retries}") int retries) {
  this.db=db;this.assets=assets;this.provider=provider;this.cacheHours=Math.max(1,cacheHours);this.dailyLimit=Math.clamp(dailyLimit,1,25);this.interval=Math.max(15,interval);this.retries=Math.clamp(retries,0,1);
 }
 @Transactional public Map<String,Object> ingest(long id) {
  var asset=assets.get(id);
  if(!provider.configured()) throw new ApiException(503,"PROVIDER_NOT_CONFIGURED","Configure ALPHA_VANTAGE_API_KEY on the backend. Existing cached records remain available.");
  boolean locked=Boolean.TRUE.equals(db.scalar("select pg_try_advisory_xact_lock(714203)",Boolean.class));
  if(!locked) throw new ApiException(429,"INGESTION_BUSY","An ingestion is already running. Retry later.");
  long recent=db.scalar("select count(*) from ingestion_runs where asset_id=? and status='SUCCESS' and completed_at>now()-(? * interval '1 hour')",Long.class,id,cacheHours);
  if(recent>0) return Map.of("status","CACHED","records_inserted",0,"message","History is cached. Refresh is allowed after the configured cache interval.");
  long today=db.scalar("select count(*) from ingestion_runs where started_at >= date_trunc('day',now() at time zone 'UTC') at time zone 'UTC'",Long.class);
  long tooSoon=db.scalar("select count(*) from ingestion_runs where started_at>now()-(? * interval '1 second')",Long.class,interval);
  if((today+1)*(retries+1)>dailyLimit||tooSoon>0) throw new ApiException(429,"PROVIDER_RATE_LIMIT","Local provider budget or minimum request interval reached. Retry later.");
  long run=db.insert("insert into ingestion_runs(asset_id,provider,status) values(?,?,'RUNNING') returning id",id,provider.name());
  List<MarketDataProvider.Bar> bars;
  try {bars=provider.fetch(asset.symbol);}catch(ApiException e) {db.update("update ingestion_runs set status='FAILED',completed_at=now(),error_message=? where id=?",e.getMessage(),run);return Map.of("status","FAILED","records_inserted",0,"message",e.getMessage());}
  int inserted=0;
  for(var b:bars) {
   inserted+=db.update("""
    insert into historical_prices(asset_id,trading_date,open,high,low,close,volume,provider,source_timestamp)
    values(?,?,?,?,?,?,?,?,?) on conflict(asset_id,trading_date,provider) do nothing
    """,id,b.date(),b.open(),b.high(),b.low(),b.close(),b.volume(),provider.name(),b.sourceTimestamp()==null?null:Timestamp.from(b.sourceTimestamp()));
  }
  db.update("update ingestion_runs set status='SUCCESS',completed_at=now(),records_inserted=? where id=?",inserted,run);
  return Map.of("status","SUCCESS","records_inserted",inserted,"message","Historical daily records cached. These are unadjusted end-of-day prices, not live quotes.");
 }
}
