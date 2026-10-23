package io.metis;
import io.metis.integration.AlphaVantageProvider;
import io.metis.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ProviderTest {
 final AlphaVantageProvider provider=new AlphaVantageProvider("",2,0,new ObjectMapper());
 @Test void noKeyFailsWithoutNetworkRequest() {var e=assertThrows(ApiException.class,()->provider.fetch("AAPL"));assertEquals("PROVIDER_NOT_CONFIGURED",e.code);}
 @Test void parsesProviderStringsWithoutInventingSourceTime() {var bars=provider.parse("""
 {"Time Series (Daily)":{"2025-01-03":{"1. open":"100.12","2. high":"102.00","3. low":"99.00","4. close":"101.40","5. volume":"1000"}}}
 """);assertEquals(1,bars.size());assertEquals("101.40",bars.getFirst().close().toPlainString());assertNull(bars.getFirst().sourceTimestamp());}
 @Test void handlesProviderQuotaAndMalformedPayload() {assertThrows(ApiException.class,()->provider.parse("{\"Information\":\"rate limit\"}"));assertThrows(ApiException.class,()->provider.parse("not json"));}
 @Test void rejectsInconsistentOhlc() {assertThrows(ApiException.class,()->provider.parse("""
 {"Time Series (Daily)":{"2025-01-03":{"1. open":"100","2. high":"80","3. low":"99","4. close":"101","5. volume":"1000"}}}
 """));}
}
