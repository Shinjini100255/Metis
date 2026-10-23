package io.metis;
import io.metis.repository.Store;
import io.metis.service.*;
import io.metis.entity.Asset;
import io.metis.integration.*;
import io.metis.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.time.LocalDate;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class IngestionServiceTest {
 Store db;AssetService assets;MarketDataProvider provider;ProviderBudget budget;MlClient ml;
 IngestionService service(boolean live,boolean fixtures) {return new IngestionService(db,assets,provider,budget,ml,new ObjectMapper(),12,0,live,fixtures);}
 @BeforeEach void setup() {
  db=mock(Store.class);assets=mock(AssetService.class);provider=mock(MarketDataProvider.class);ml=mock(MlClient.class);budget=mock(ProviderBudget.class);
  when(ml.post(eq("/data/plan"),any())).thenReturn(Map.of("compact_missing_sessions",1,"missing_sessions",20));
  when(db.scalar(anyString(),eq(Long.class),any(Object[].class))).thenReturn(0L);
  when(assets.get(1)).thenReturn(new Asset("AAPL","Apple"));when(provider.configured()).thenReturn(true);when(provider.name()).thenReturn("Alpha Vantage");
  when(db.scalar("select pg_try_advisory_xact_lock(714203)",Boolean.class)).thenReturn(true);
 }
 @Test void disabledByDefaultNeverCallsProvider() {assertEquals("LIVE_INGESTION_DISABLED",assertThrows(ApiException.class,()->service(false,false).ingest(1)).code);verify(provider,never()).fetch(anyString());verifyNoInteractions(budget);}
 @Test void missingCredentialsNeverCallsProvider() {when(provider.configured()).thenReturn(false);assertEquals(503,assertThrows(ApiException.class,()->service(true,false).ingest(1)).status);verify(provider,never()).fetch(anyString());}
 @Test void cachedAssetDoesNotRefetch() {when(db.scalar(contains("completed_at>"),eq(Long.class),eq(1L),eq(12))).thenReturn(1L);assertEquals("CACHED",service(true,false).ingest(1).get("status"));verifyNoInteractions(budget);verify(provider,never()).fetch(anyString());}
 @Test void networkFailureIsRecorded() {
  when(db.insert(anyString(),any(),any())).thenReturn(7L);when(budget.reserve()).thenReturn(3L);
  when(provider.fetch("AAPL")).thenThrow(new ApiException(502,"PROVIDER_NETWORK","Provider unavailable."));
  var result=service(true,false).ingest(1);assertEquals("FAILED",result.get("status"));assertEquals(1,result.get("request_count"));verify(budget).finish(3L,"PROVIDER_NETWORK");
  verify(db,never()).update(contains("insert into historical_prices"),any(Object[].class));
 }
 @Test void planDeduplicatesWithoutNetwork() {
  var r=service(false,false).plan(List.of(1L,1L),LocalDate.of(2025,1,1),LocalDate.of(2025,12,31));assertEquals(1,r.get("estimated_requests"));assertEquals(0,r.get("external_requests_made"));verify(provider,never()).fetch(anyString());verify(ml).post(eq("/data/plan"),any());
 }
 @Test void fixtureImportRequiresIsolatedServerFlag() {assertEquals("FIXTURE_DISABLED",assertThrows(ApiException.class,()->service(false,false).importLocal(1,List.of(Map.of("close",10)),true)).code);verifyNoInteractions(ml);}
}
