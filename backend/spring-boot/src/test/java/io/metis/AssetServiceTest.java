package io.metis;
import io.metis.entity.Asset;
import io.metis.repository.AssetRepository;
import io.metis.service.AssetService;
import io.metis.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class AssetServiceTest {
 final AssetRepository repo=mock(AssetRepository.class);final AssetService service=new AssetService(repo);
 @Test void searchUsesBoundedPage() {when(repo.search(eq("AAP"),any(Pageable.class))).thenReturn(new PageImpl<>(List.of(new Asset("AAPL","Apple"))));var result=service.search(" AAP ",0,20);assertEquals(1,result.items().size());assertEquals(20,result.size());verify(repo).search(eq("AAP"),argThat(p->p.getPageSize()==20));}
 @Test void missingAssetIs404() {when(repo.findById(42L)).thenReturn(Optional.empty());assertEquals(404,assertThrows(ApiException.class,()->service.get(42)).status);}
}
