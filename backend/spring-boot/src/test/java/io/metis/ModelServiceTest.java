package io.metis;
import io.metis.service.*;
import io.metis.repository.Store;
import io.metis.integration.MlClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.dao.DataAccessResourceFailureException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ModelServiceTest {
 @Test void schedulerRecoversAfterTemporaryDatabaseOutageWithoutProviderOrRetraining() {
  var db=mock(Store.class);var ml=mock(MlClient.class);
  when(db.rows(contains("training_jobs"))).thenThrow(new DataAccessResourceFailureException("Database restarting")).thenReturn(List.of());
  var service=new ModelService(db,mock(AssetService.class),mock(PriceService.class),ml,new ObjectMapper(),mock(PlatformTransactionManager.class),false);
  assertDoesNotThrow(service::refresh);assertDoesNotThrow(service::refresh);
  verify(db,times(2)).rows(contains("training_jobs"));verify(db).update(contains("update model_predictions"));verifyNoInteractions(ml);
 }
}
