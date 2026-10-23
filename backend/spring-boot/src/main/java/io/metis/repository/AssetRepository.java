package io.metis.repository;
import io.metis.entity.Asset;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface AssetRepository extends JpaRepository<Asset,Long> {
 @Query("select a from Asset a where lower(a.symbol) like lower(concat('%',:q,'%')) or lower(a.companyName) like lower(concat('%',:q,'%'))")
 Page<Asset> search(@Param("q") String q,Pageable page);
 Optional<Asset> findBySymbol(String symbol);
}
