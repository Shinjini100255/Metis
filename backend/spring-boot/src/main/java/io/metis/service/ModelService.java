package io.metis.service;
import io.metis.repository.Store;
import io.metis.integration.MlClient;
import io.metis.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
@Service
public class ModelService {
 private final Store db;private final AssetService assets;private final PriceService prices;private final MlClient ml;private final ObjectMapper json;private final TransactionTemplate tx;private final boolean allowFixtures;
 public ModelService(Store db,AssetService assets,PriceService prices,MlClient ml,ObjectMapper json,PlatformTransactionManager manager,@Value("${metis.allow-fixtures:false}") boolean allowFixtures) {this.db=db;this.assets=assets;this.prices=prices;this.ml=ml;this.json=json;this.tx=new TransactionTemplate(manager);this.allowFixtures=allowFixtures;}
 private String encode(Object value) {try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Metadata serialization failed");}}
 @SuppressWarnings("unchecked") private Map<String,Object> decode(Object value) {try{return json.readValue(value.toString(),Map.class);}catch(Exception e){throw new IllegalStateException("Stored metadata is invalid");}}
 public Map<String,Object> snapshot(long assetId) {
  var asset=assets.get(assetId);var end=LocalDate.now(ZoneOffset.UTC);var bars=prices.history(assetId,end.minusYears(5),end);
  if(bars.isEmpty()) throw new ApiException(422,"NO_HISTORICAL_DATA","No stored historical data. Import sufficient local OHLCV history; training never downloads data.");
  String source=bars.getLast().get("provider").toString();boolean fixture=source.equals("TEST_FIXTURE");
  if(fixture&&!allowFixtures) throw new ApiException(403,"FIXTURE_DISABLED","Fixture series cannot be used in the production registry.");
  return new LinkedHashMap<>(Map.of("asset_id",assetId,"symbol",asset.symbol,"provider",source,"price_basis","UNADJUSTED","development_only",fixture,"bars",bars));
 }
 public Object features(long id) {return ml.post("/features",snapshot(id));}
 public Object submit(long id,Map<String,Object> config) {
  var payload=snapshot(id);String job=UUID.randomUUID().toString();payload.put("job_id",job);payload.put("configuration",config);
  return tx.execute(ignored->{
   db.scalar("select pg_advisory_xact_lock(714205)",Object.class);
   if(db.scalar("select count(*) from training_jobs where status in ('QUEUED','RUNNING')",Long.class)>0) throw new ApiException(409,"TRAINING_BUSY","One training job is already active.");
   db.update("insert into training_jobs(id,asset_id,status,development_only,configuration) values(?,?,'QUEUED',?,?::jsonb)",job,id,payload.get("development_only"),encode(config));
   try {var result=ml.post("/training/jobs",payload);db.update("update training_jobs set status=?,message=? where id=?",result.get("status"),result.get("message"),job);}
   catch(ApiException e) {db.update("update training_jobs set status='FAILED',message=?,completed_at=now() where id=?",e.getMessage(),job);return Map.of("id",job,"status","FAILED","message",e.getMessage());}
   return Map.of("id",job,"status","RUNNING","message","Sequential CPU training started from stored data; no provider requests.");
  });
 }
 @SuppressWarnings("unchecked") private void complete(String id,Map<String,Object> state) {
  tx.executeWithoutResult(ignored->{
   var job=db.one("select * from training_jobs where id=? for update",id);
   if(!List.of("QUEUED","RUNNING").contains(job.get("status"))) return;
   String status=state.get("status").toString();
   if(status.equals("SUCCEEDED")) {
    var result=(Map<String,Object>)state.get("result");var dataset=(Map<String,Object>)result.get("dataset");
    boolean fixture=Boolean.TRUE.equals(dataset.get("development_only"));
    if(fixture&&!allowFixtures) throw new ApiException(403,"FIXTURE_DISABLED","Development artifacts cannot be registered here.");
    db.update("insert into training_datasets(id,asset_id,provider,development_only,metadata) values(?,?,?,?,?::jsonb) on conflict(id) do nothing",dataset.get("dataset_id"),job.get("asset_id"),dataset.get("provider"),fixture,encode(dataset));
    for(var m:(List<Map<String,Object>>)result.get("models")) {
     db.update("insert into model_versions(model_name,version,status,artifact_reference,asset_id,algorithm,task,dataset_id,metadata,development_only) values(?,?,'READY',?,?,?,?,?,?::jsonb,?) on conflict(model_name,version) do nothing",m.get("algorithm")+" / "+m.get("task"),m.get("artifact_id"),m.get("artifact_id"),job.get("asset_id"),m.get("algorithm"),m.get("task"),dataset.get("dataset_id"),encode(m),fixture);
    }
    db.update("update training_jobs set dataset_id=? where id=?",dataset.get("dataset_id"),id);
   }
   db.update("update training_jobs set status=?,message=?,completed_at=now() where id=?",status,state.get("message"),id);
  });
 }
 @Scheduled(fixedDelay=5000,initialDelay=10000) public void refresh() {
  try {reconcile();}
  catch(org.springframework.dao.DataAccessException e) {org.slf4j.LoggerFactory.getLogger(ModelService.class).warn("Database temporarily unavailable; model reconciliation will resume on the next scheduled check.");}
 }
 private void reconcile() {
  for(var job:db.rows("select id,started_at from training_jobs where status in ('QUEUED','RUNNING') order by started_at limit 1")) {
   String id=job.get("id").toString();
   try {var state=ml.get("/training/jobs/"+id);if(!List.of("QUEUED","RUNNING").contains(state.get("status"))) complete(id,state);}
   catch(Exception e) {org.slf4j.LoggerFactory.getLogger(ModelService.class).warn("Training status reconciliation failed for job {}: {}",id,e.getClass().getSimpleName());if(((java.sql.Timestamp)job.get("started_at")).toInstant().isBefore(Instant.now().minusSeconds(660))) db.update("update training_jobs set status='INTERRUPTED',message='Model service status unavailable beyond the bounded job window.',completed_at=now() where id=?",id);}
  }
  // Actual outcomes are filled only from the same stored provider series, without any download.
  db.update("""
   update model_predictions p set actual_return=(t.close/f.close-1),actual_direction=case when t.close>f.close then 1 else 0 end,status='RESOLVED',resolved_at=now()
   from historical_prices f,historical_prices t where p.status='FORWARD' and not p.development_only
    and f.asset_id=p.asset_id and f.trading_date=p.feature_date and f.provider=p.provider
    and t.asset_id=p.asset_id and t.trading_date=p.prediction_target_date and t.provider=p.provider
   """);
 }
 public Object jobs() {return db.rows("select * from training_jobs order by started_at desc limit 50").stream().map(row->{row.put("configuration",decode(row.get("configuration")));return row;}).toList();}
 public Object models() {return db.rows("select * from model_versions where (? or not development_only) order by created_at desc,id desc limit 100",allowFixtures).stream().map(row->{row.put("metadata",decode(row.get("metadata")));return row;}).toList();}
 public Object operations() {return Map.of("training_runs",jobs(),"evaluations",models(),"deployment_status","LOCAL_ARTIFACT_REGISTRY","promotion_status","CV_SELECTION");}
 @SuppressWarnings("unchecked") public Object predict(long assetId,long modelId) {
  var m=db.one("select * from model_versions where id=? and asset_id=? and status='READY'",modelId,assetId);
  if(Boolean.TRUE.equals(m.get("development_only"))&&!allowFixtures) throw new ApiException(403,"FIXTURE_DISABLED","Development inference is disabled.");
  var payload=snapshot(assetId);payload.put("artifact_id",m.get("artifact_reference"));var r=ml.post("/predict",payload);
  db.update("""
   insert into model_predictions(model_version_id,asset_id,prediction_target_date,predicted_value,probability,feature_date,feature_timestamp,target_horizon,task,provider,status,data_age_seconds,development_only)
   values(?,?,?::date,?,?,?::date,?::timestamptz,?,?,?,?,?,?) on conflict(model_version_id,asset_id,feature_date) where feature_date is not null do nothing
   """,modelId,assetId,r.get("target_date"),r.get("predicted_value"),r.get("probability"),r.get("feature_date"),r.get("feature_timestamp"),r.get("target_horizon"),r.get("task"),payload.get("provider"),r.get("status"),r.get("data_age_seconds"),r.get("development_only"));
  return db.one("select * from model_predictions where model_version_id=? and asset_id=? and feature_date=?::date",modelId,assetId,r.get("feature_date"));
 }
 public Object coverage() {return db.rows("select a.id as asset_id,a.symbol,p.provider,count(p.id) as observations,min(p.trading_date) as first_date,max(p.trading_date) as last_date from assets a left join historical_prices p on p.asset_id=a.id group by a.id,a.symbol,p.provider order by a.symbol");}
}
