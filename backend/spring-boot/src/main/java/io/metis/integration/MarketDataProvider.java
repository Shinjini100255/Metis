package io.metis.integration;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
public interface MarketDataProvider {
 record Bar(LocalDate date,BigDecimal open,BigDecimal high,BigDecimal low,BigDecimal close,long volume,Instant sourceTimestamp) {}
 String name(); boolean configured(); List<Bar> fetch(String symbol);
}
