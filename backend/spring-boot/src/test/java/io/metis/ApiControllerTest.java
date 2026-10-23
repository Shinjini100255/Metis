package io.metis;
import io.metis.controller.ApiController;
import io.metis.service.*;
import io.metis.repository.Store;
import io.metis.exception.*;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
class ApiControllerTest {
 MockMvc mvc;AssetService assets;PortfolioService portfolios;SystemService system;
 @BeforeEach void setup() {assets=mock(AssetService.class);portfolios=mock(PortfolioService.class);system=mock(SystemService.class);mvc=MockMvcBuilders.standaloneSetup(new ApiController(assets,mock(PriceService.class),mock(IndicatorService.class),mock(WatchlistService.class),portfolios,mock(IngestionService.class),system,mock(CatalogService.class))).setControllerAdvice(new ApiErrors()).build();}
 @Test void rejectsInvalidHolding() throws Exception {mvc.perform(post("/api/v1/portfolios/1/holdings").contentType("application/json").content("{\"assetId\":1,\"quantity\":-1,\"averagePurchasePrice\":100}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));verifyNoInteractions(portfolios);}
 @Test void missingAssetReturnsSafeError() throws Exception {when(assets.get(9)).thenThrow(new ApiException(404,"ASSET_NOT_FOUND","Asset not found."));mvc.perform(get("/api/v1/assets/9")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ASSET_NOT_FOUND")).andExpect(jsonPath("$.trace").doesNotExist());}
 @Test void healthUsesMeasuredContract() throws Exception {when(system.status()).thenReturn(java.util.Map.of("spring",java.util.Map.of("status","UP")));mvc.perform(get("/api/v1/health")).andExpect(status().isOk()).andExpect(jsonPath("$.spring.status").value("UP"));}
 @Test void unexpectedErrorDoesNotLeakException() throws Exception {when(assets.get(1)).thenThrow(new IllegalStateException("password=secret"));mvc.perform(get("/api/v1/assets/1")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.message").value("The request could not be completed. Check service health."));}
}
