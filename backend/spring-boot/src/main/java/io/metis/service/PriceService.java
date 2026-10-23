package io.metis.service;
import io.metis.repository.Store;
import io.metis.dto.Requests.PageResult;
import io.metis.exception.ApiException;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;
@Service
public class PriceService {
 private final Store db;private final AssetService assets;
 public PriceService(Store db,AssetService assets) {this.db=db;this.assets=assets;}
 public List<Map<String,Object>> history(long id,LocalDate from,LocalDate to) {
  assets.get(id);if(to.isBefore(from)||from.isBefore(to.minusYears(5))) throw new ApiException(400,"DATE_RANGE","Choose a date range of at most five years.");
  return db.rows("select trading_date,open,high,low,close,adjusted_close,volume,provider,source_timestamp,ingested_at from historical_prices where asset_id=? and provider=(select provider from historical_prices where asset_id=? order by trading_date desc,ingested_at desc limit 1) and trading_date between ? and ? order by trading_date limit 1260",id,id,from,to);
 }
 public PageResult<Map<String,Object>> prices(long id,LocalDate from,LocalDate to,int page,int size) {
  var all=history(id,from,to);return new PageResult<>(all.stream().skip((long)page*size).limit(size).toList(),all.size(),page,size);
 }
 public List<Map<String,Object>> quotes() {
  return db.rows("""
   select a.id as asset_id,a.symbol,a.company_name,a.exchange,a.currency,p.close,p.volume,p.trading_date,p.ingested_at,p.provider,
    case when prev.close>0 then (p.close/prev.close-1)*100 else null end as change_percent
   from assets a left join lateral (select * from historical_prices where asset_id=a.id order by trading_date desc,ingested_at desc limit 1) p on true
   left join lateral (select close from historical_prices where asset_id=a.id and provider=p.provider and trading_date<p.trading_date order by trading_date desc limit 1) prev on true
   order by a.symbol limit 100
   """);
 }
}
