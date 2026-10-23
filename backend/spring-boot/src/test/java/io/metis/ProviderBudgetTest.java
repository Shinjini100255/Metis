package io.metis;
import io.metis.repository.Store;
import io.metis.service.ProviderBudget;
import io.metis.exception.ApiException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ProviderBudgetTest {
 @Test void persistentFailedAttemptsConsumeBudget() {
  Store db=mock(Store.class);when(db.scalar(anyString(),eq(Long.class),any(Object[].class))).thenReturn(0L);when(db.scalar(contains("1 minute"),eq(Long.class))).thenReturn(4L);
  var b=new ProviderBudget(db,4,20,15,2);assertEquals("PROVIDER_RATE_LIMIT",assertThrows(ApiException.class,b::reserve).code);
  verify(db,never()).insert(anyString(),any(Object[].class));
 }
}
