package io.metis;
import io.metis.repository.Store;
import io.metis.service.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class PortfolioServiceTest {
 final Store db=mock(Store.class);final PortfolioService service=new PortfolioService(db,mock(AssetService.class));
 Map<String,Object> holding(String currency,BigDecimal price) {var h=new HashMap<String,Object>();h.put("quantity",new BigDecimal("2"));h.put("average_purchase_price",new BigDecimal("100.10"));h.put("currency",currency);h.put("latest_price",price);return h;}
 void setup(Map<String,Object> holding) {when(db.one(anyString(),eq(1L))).thenReturn(new HashMap<>(Map.of("base_currency","USD")));when(db.rows(anyString(),eq(1L))).thenReturn(List.of(holding));}
 @Test void exactDecimalValuation() {setup(holding("USD",new BigDecimal("110.20")));var result=service.get(1);assertEquals(new BigDecimal("220.40"),result.get("market_value"));assertEquals(new BigDecimal("20.20"),result.get("unrealized_pl"));}
 @Test void missingPriceIsNotZero() {setup(holding("USD",null));var result=service.get(1);assertNull(result.get("market_value"));assertNull(result.get("unrealized_pl"));assertEquals(new BigDecimal("200.20"),result.get("invested_capital"));assertEquals(1,result.get("unvalued_positions"));}
 @Test void mismatchedCurrenciesDoNotAggregate() {setup(holding("INR",new BigDecimal("110.20")));var result=service.get(1);assertNull(result.get("invested_capital"));assertNull(result.get("market_value"));assertEquals(1,result.get("currency_mismatches"));}
}
