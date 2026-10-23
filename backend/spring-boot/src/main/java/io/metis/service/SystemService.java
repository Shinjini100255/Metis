package io.metis.service;
import io.metis.repository.Store;
import io.metis.integration.*;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
@Service
public class SystemService {
 private final Store db;private final MlClient ml;private final MarketDataProvider provider;
 public SystemService(Store db,MlClient ml,MarketDataProvider provider) {this.db=db;this.ml=ml;this.provider=provider;}
 public Map<String,Object> status() {
  var out=new LinkedHashMap<String,Object>();out.put("spring",Map.of("status","UP"));
  long start=System.nanoTime();
  try {db.scalar("select 1",Integer.class);out.put("postgres",Map.of("status","UP","response_ms",(System.nanoTime()-start)/1_000_000));}
  catch(Exception e) {out.put("postgres",Map.of("status","DOWN"));}
  start=System.nanoTime();
  try {var h=ml.health();out.put("ml",Map.of("status",h==null?"DOWN":h.status(),"response_ms",(System.nanoTime()-start)/1_000_000));}
  catch(Exception e) {out.put("ml",Map.of("status","UNAVAILABLE","response_ms",(System.nanoTime()-start)/1_000_000));}
  var runs=db.rows("select status,completed_at,error_message from ingestion_runs order by started_at desc limit 1");
  var providerState=new LinkedHashMap<String,Object>();providerState.put("name",provider.name());providerState.put("configured",provider.configured());
  providerState.put("status",!provider.configured()?"NOT_CONFIGURED":runs.isEmpty()?"UNTESTED":runs.getFirst().get("status").equals("SUCCESS")?"LAST_REQUEST_SUCCEEDED":"DEGRADED");
  providerState.put("last_request",runs.isEmpty()?null:runs.getFirst());providerState.put("data_type","Historical daily, unadjusted; not real-time");out.put("provider",providerState);
  out.put("last_successful_ingestion",db.rows("select completed_at,records_inserted from ingestion_runs where status='SUCCESS' order by completed_at desc limit 1").stream().findFirst().orElse(null));
  out.put("timestamp",Instant.now());out.put("environment","single-user local development");return out;
 }
 public Object modelStatus() {
  try{return ml.status();}catch(Exception e) {return Map.of("available",false,"status","UNAVAILABLE","message","Model service could not be reached.");}
 }
}
