package io.metis;
import io.metis.repository.Store;
import io.metis.service.*;
import io.metis.entity.Asset;
import io.metis.integration.MarketDataProvider;
import io.metis.exception.ApiException;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class IngestionServiceTest {
 JdbcTemplate jdbc;AssetService assets;MarketDataProvider provider;IngestionService service;
 @BeforeEach void setup() {
  jdbc=mock(JdbcTemplate.class);assets=mock(AssetService.class);provider=mock(MarketDataProvider.class);
  when(assets.get(1)).thenReturn(new Asset("AAPL","Apple"));when(provider.configured()).thenReturn(true);when(provider.name()).thenReturn("Alpha Vantage");
  when(jdbc.queryForObject("select pg_try_advisory_xact_lock(714203)",Boolean.class)).thenReturn(true);
  service=new IngestionService(new Store(jdbc),assets,provider,12,25,15,0);
 }
 @Test void missingCredentialsNeverCallsProvider() {when(provider.configured()).thenReturn(false);var e=assertThrows(ApiException.class,()->service.ingest(1));assertEquals(503,e.status);verify(provider,never()).fetch(anyString());}
 @Test void concurrentIngestionIsRejected() {when(jdbc.queryForObject("select pg_try_advisory_xact_lock(714203)",Boolean.class)).thenReturn(false);assertEquals(429,assertThrows(ApiException.class,()->service.ingest(1)).status);verify(provider,never()).fetch(anyString());}
 @Test void cachedAssetDoesNotRefetch() {when(jdbc.queryForObject(contains("completed_at>"),eq(Long.class),eq(1L),eq(12))).thenReturn(1L);var result=service.ingest(1);assertEquals("CACHED",result.get("status"));verify(provider,never()).fetch(anyString());}
 @Test void networkFailureIsRecordedAndNoPriceIsInvented() {
  when(jdbc.queryForObject(contains("completed_at>"),eq(Long.class),eq(1L),eq(12))).thenReturn(0L);
  when(jdbc.queryForObject(contains("date_trunc"),eq(Long.class))).thenReturn(0L);
  when(jdbc.queryForObject(contains("started_at>"),eq(Long.class),eq(15))).thenReturn(0L);
  when(jdbc.queryForObject(contains("returning id"),eq(Long.class),eq(1L),eq("Alpha Vantage"))).thenReturn(7L);
  when(provider.fetch("AAPL")).thenThrow(new ApiException(502,"PROVIDER_NETWORK","Provider unavailable."));
  var result=service.ingest(1);assertEquals("FAILED",result.get("status"));assertEquals(0,result.get("records_inserted"));
  verify(jdbc).update(contains("status='FAILED'"),eq("Provider unavailable."),eq(7L));
  verify(jdbc,never()).update(contains("insert into historical_prices"),any(Object[].class));
 }
}
