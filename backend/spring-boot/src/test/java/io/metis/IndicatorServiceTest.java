package io.metis;
import io.metis.service.IndicatorService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class IndicatorServiceTest {
 final IndicatorService service=new IndicatorService();
 @Test void noDataRemainsUnavailable() {var r=service.calculate(List.of());assertNull(r.get("annualized_volatility"));assertNull(r.get("maximum_drawdown"));assertEquals(0,r.get("observations"));}
 @Test void computesReturnAndDrawdown() {var r=service.calculate(prices(100,110,88));assertEquals(-0.2,(Double)r.get("maximum_drawdown"),1e-9);var points=(List<Map<String,Object>>)r.get("points");assertEquals(-0.2,(Double)points.getLast().get("daily_return"),1e-9);assertNull(points.getLast().get("rsi14"));}
 @Test void computesSmaAndWilderRsi() {double[] close=new double[21];for(int i=0;i<21;i++) close[i]=100+i;var r=service.calculate(prices(close));var points=(List<Map<String,Object>>)r.get("points");assertEquals(110.5,(Double)points.getLast().get("sma20"),1e-9);assertEquals(100.0,points.getLast().get("rsi14"));assertNotNull(r.get("annualized_volatility"));assertNotNull(r.get("sharpe_ratio"));}
 @Test void flatPricesHaveZeroVolAndUndefinedSharpe() {double[] close=new double[30];Arrays.fill(close,100);var r=service.calculate(prices(close));assertEquals(0.0,r.get("annualized_volatility"));assertNull(r.get("sharpe_ratio"));var points=(List<Map<String,Object>>)r.get("points");assertEquals(50.0,points.getLast().get("rsi14"));}
 static List<Map<String,Object>> prices(double... values) {var p=new ArrayList<Map<String,Object>>();for(int i=0;i<values.length;i++) p.add(Map.of("close",values[i],"trading_date","session-"+i));return p;}
}
