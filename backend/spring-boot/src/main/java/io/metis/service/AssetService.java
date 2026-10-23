package io.metis.service;
import io.metis.dto.Requests.*;
import io.metis.entity.Asset;
import io.metis.repository.AssetRepository;
import io.metis.exception.ApiException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
@Service
public class AssetService {
 private final AssetRepository repo;
 public AssetService(AssetRepository repo) { this.repo=repo; }
 public PageResult<Asset> search(String q,int page,int size) {
  var r=repo.search(q.trim(),PageRequest.of(page,size,Sort.by("symbol")));return new PageResult<>(r.getContent(),r.getTotalElements(),page,size);
 }
 public Asset get(long id) { return repo.findById(id).orElseThrow(()->new ApiException(404,"ASSET_NOT_FOUND","Asset not found.")); }
 public Asset add(NewAsset request) { return repo.findBySymbol(request.symbol()).orElseGet(()->repo.save(new Asset(request.symbol(),request.companyName().trim()))); }
}
