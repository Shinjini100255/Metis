package io.metis;
import io.metis.service.IndicatorService;
import io.metis.integration.MlClient;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class IndicatorServiceTest {
 @Test void delegatesToCanonicalPythonEngine() {
  MlClient ml=mock(MlClient.class);var expected=Map.<String,Object>of("points",List.of(),"observations",0);
  when(ml.post(eq("/indicators"),any())).thenReturn(expected);
  assertSame(expected,new IndicatorService(ml).calculate(List.of()));
 }
}
