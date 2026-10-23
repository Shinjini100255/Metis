package io.metis.service;
import io.metis.integration.MlClient;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class IndicatorService {
 private final MlClient ml;
 public IndicatorService(MlClient ml) {this.ml=ml;}
 public Map<String,Object> calculate(List<Map<String,Object>> prices) {
  return ml.post("/indicators",Map.of("asset_id",1,"symbol","LOCAL","bars",prices));
 }
}
