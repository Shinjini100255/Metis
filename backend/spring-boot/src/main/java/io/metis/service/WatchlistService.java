package io.metis.service;
import io.metis.repository.Store;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class WatchlistService {
 private final Store db; private final AssetService assets;
 public WatchlistService(Store db,AssetService assets) {this.db=db;this.assets=assets;}
 public List<Map<String,Object>> list() {
  var lists=db.rows("select * from watchlists order by id limit 100");
  for(var w:lists) w.put("assets",db.rows("select a.* from assets a join watchlist_items i on i.asset_id=a.id where i.watchlist_id=? order by a.symbol limit 100",w.get("id")));
  return lists;
 }
 public Map<String,Object> create(String name) { long id=db.insert("insert into watchlists(name) values(?) returning id",name.trim());return db.one("select * from watchlists where id=?",id); }
 @Transactional public void add(long id,long assetId) {
  db.one("select id from watchlists where id=? for update",id);assets.get(assetId);
  long count=db.scalar("select count(*) from watchlist_items where watchlist_id=?",Long.class,id);
  if(count>=100) throw new io.metis.exception.ApiException(400,"LIMIT","A watchlist supports up to 100 assets.");
  db.update("insert into watchlist_items(watchlist_id,asset_id) values(?,?) on conflict do nothing",id,assetId);
 }
 public void remove(long id,long assetId) { db.one("select id from watchlists where id=?",id);db.update("delete from watchlist_items where watchlist_id=? and asset_id=?",id,assetId); }
}
