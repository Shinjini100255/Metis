package io.metis.controller;
import io.metis.service.ModelService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import java.util.*;
@RestController @RequestMapping("/api/v1") @Validated
public class ModelController {
 private final ModelService models;
 public ModelController(ModelService models) {this.models=models;}
 public record Train(@Positive long assetId,Map<String,Object> configuration) {}
 public record Infer(@Positive long assetId,@Positive long modelVersionId) {}
 @GetMapping("/assets/{id}/features") public Object features(@PathVariable @Positive long id) {return models.features(id);}
 @GetMapping("/data/coverage") public Object coverage() {return models.coverage();}
 @GetMapping("/training/jobs") public Object jobs() {return models.jobs();}
 @PostMapping("/training/jobs") @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED) public Object train(@Valid @RequestBody Train r) {return models.submit(r.assetId(),r.configuration()==null?Map.of():r.configuration());}
 @PostMapping("/predictions") public Object predict(@Valid @RequestBody Infer r) {return models.predict(r.assetId(),r.modelVersionId());}
}
