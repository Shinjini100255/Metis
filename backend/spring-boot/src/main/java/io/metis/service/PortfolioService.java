package io.metis.service;
import io.metis.repository.Store;
import io.metis.dto.Requests.*;
import io.metis.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
@Service
public class PortfolioService {
 private final Store db; private final AssetService assets;
 public PortfolioService(Store db,AssetService assets) {this.db=db;this.assets=assets;}
 public List<Map<String,Object>> list() { return db.rows("select * from portfolios order by created_at desc,id desc limit 100"); }
 public Map<String,Object> create(Portfolio r) { long id=db.insert("insert into portfolios(name,base_currency) values(?,?) returning id",r.name().trim(),r.baseCurrency());return get(id); }
 public Map<String,Object> get(long id) {
  var p=db.one("select * from portfolios where id=?",id);
  var holdings=db.rows("""
   select h.*,a.symbol,a.company_name,a.currency,p.close as latest_price,p.trading_date as price_date,p.provider
   from portfolio_holdings h join assets a on a.id=h.asset_id
   left join lateral (select close,trading_date,provider from historical_prices where asset_id=a.id order by trading_date desc,ingested_at desc limit 1) p on true
   where h.portfolio_id=? order by a.symbol
   """,id);
  BigDecimal invested=BigDecimal.ZERO,knownValue=BigDecimal.ZERO;int missing=0,mismatched=0;
  for(var h:holdings) {
   var cost=((BigDecimal)h.get("quantity")).multiply((BigDecimal)h.get("average_purchase_price"));h.put("invested_capital",cost);
   boolean match=p.get("base_currency").equals(h.get("currency"));
   if(match) invested=invested.add(cost);else mismatched++;
   BigDecimal price=(BigDecimal)h.get("latest_price");
   BigDecimal value=price==null?null:price.multiply((BigDecimal)h.get("quantity"));
   h.put("position_value",value);h.put("unrealized_pl",value==null?null:value.subtract(cost));
   h.put("valuation_status",!match?"CURRENCY_MISMATCH":price==null?"MISSING_PRICE":"HISTORICAL");
   if(match && value!=null) knownValue=knownValue.add(value);else missing++;
  }
  p.put("holdings",holdings);p.put("invested_capital",mismatched==0?invested:null);p.put("known_value",knownValue);
  p.put("market_value",missing==0?knownValue:null);p.put("unrealized_pl",missing==0?knownValue.subtract(invested):null);
  p.put("unvalued_positions",missing);p.put("currency_mismatches",mismatched);return p;
 }
 @Transactional public void add(long id,Holding r) {
  db.one("select id from portfolios where id=? for update",id);assets.get(r.assetId());
  long count=db.scalar("select count(*) from portfolio_holdings where portfolio_id=?",Long.class,id);
  if(count>=100) throw new ApiException(400,"LIMIT","A portfolio supports up to 100 positions.");
  db.update("insert into portfolio_holdings(portfolio_id,asset_id,quantity,average_purchase_price) values(?,?,?,?)",id,r.assetId(),r.quantity(),r.averagePurchasePrice());
 }
 @Transactional public void update(long id,long holdingId,Holding r) { assets.get(r.assetId());if(db.update("update portfolio_holdings set asset_id=?,quantity=?,average_purchase_price=?,updated_at=now() where id=? and portfolio_id=?",r.assetId(),r.quantity(),r.averagePurchasePrice(),holdingId,id)==0) throw new ApiException(404,"NOT_FOUND","Holding not found in this portfolio."); }
 public void remove(long id,long holdingId) { if(db.update("delete from portfolio_holdings where id=? and portfolio_id=?",holdingId,id)==0) throw new ApiException(404,"NOT_FOUND","Holding not found in this portfolio."); }
 public void delete(long id) { if(db.update("delete from portfolios where id=?",id)==0) throw new ApiException(404,"NOT_FOUND","Portfolio not found."); }
}
