package io.metis.service;
import io.metis.repository.Store;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class CatalogService {
 private final Store db;private final ModelService models;
 public CatalogService(Store db,ModelService models) {this.db=db;this.models=models;}
 public Object predictions(Long assetId,int page,int size) {return db.rows("select * from model_predictions where (?::bigint is null or asset_id=?) order by created_at desc limit ? offset ?",assetId,assetId,size,(long)page*size);}
 public Object models() {return models.models();}
 public Object operations() {return models.operations();}
 public Object strategies() {return db.rows("select *, 'NOT_EXECUTABLE' as readiness from strategies order by id limit 100");}
 public Object backtests() {return db.rows("select * from backtest_runs order by created_at desc limit 100");}
 public Object ingestion() {return db.rows("select r.*,a.symbol from ingestion_runs r join assets a on a.id=r.asset_id order by started_at desc limit 50");}
}
