package io.metis.repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public class Store {
 private final JdbcTemplate jdbc;
 public Store(JdbcTemplate jdbc) { this.jdbc=jdbc; }
 public <T> T scalar(String sql,Class<T> type,Object...args) { return args.length==0?jdbc.queryForObject(sql,type):jdbc.queryForObject(sql,type,args); }
 public List<Map<String,Object>> rows(String sql,Object...args) { return jdbc.queryForList(sql,args); }
 public Map<String,Object> one(String sql,Object...args) {
  var rows=rows(sql,args); if(rows.isEmpty()) throw new io.metis.exception.ApiException(404,"NOT_FOUND","Record not found."); return rows.getFirst();
 }
 public long insert(String sql,Object...args) { return jdbc.queryForObject(sql,Long.class,args); }
 public int update(String sql,Object...args) { return jdbc.update(sql,args); }
}
