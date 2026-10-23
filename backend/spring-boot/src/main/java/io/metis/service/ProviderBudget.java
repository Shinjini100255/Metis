package io.metis.service;
import io.metis.repository.Store;
import io.metis.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.*;
@Service
public class ProviderBudget {
 private final Store db; public final int minute,day,interval,job;
 public ProviderBudget(Store db,@Value("${metis.market-minute-limit:4}") int minute,@Value("${metis.market-daily-limit:20}") int day,@Value("${metis.market-min-interval-seconds:15}") int interval,@Value("${metis.market-job-limit:2}") int job) {
  this.db=db;this.minute=Math.clamp(minute,1,60);this.day=Math.clamp(day,1,10000);this.interval=Math.max(1,interval);this.job=Math.clamp(job,1,10);
 }
 public int minuteLimit(){return minute;} public int dayLimit(){return day;} public int minimumInterval(){return interval;} public int jobLimit(){return job;}
 @Transactional(propagation=Propagation.REQUIRES_NEW) public long reserve() {
  db.scalar("select pg_advisory_xact_lock(714204)",Object.class);
  long minuteUsed=db.scalar("select count(*) from provider_requests where provider='Alpha Vantage' and requested_at>now()-interval '1 minute'",Long.class);
  long dayUsed=db.scalar("select count(*) from provider_requests where provider='Alpha Vantage' and requested_at >= date_trunc('day',now() at time zone 'UTC') at time zone 'UTC'",Long.class);
  long recent=db.scalar("select count(*) from provider_requests where provider='Alpha Vantage' and requested_at>now()-(? * interval '1 second')",Long.class,interval);
  if(minuteUsed>=minute||dayUsed>=day||recent>0) throw new ApiException(429,"PROVIDER_RATE_LIMIT","Persistent local request budget or interval reached. No request sent.");
  return db.insert("insert into provider_requests(provider) values('Alpha Vantage') returning id");
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW) public void finish(long id,String outcome) {db.update("update provider_requests set outcome=? where id=?",outcome,id);}
}
