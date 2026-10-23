package io.metis.service;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class IndicatorService {
 public Map<String,Object> calculate(List<Map<String,Object>> prices) {
  var points=new ArrayList<Map<String,Object>>();var returns=new ArrayList<Double>();
  double peak=0,drawdown=0,gain=0,loss=0;
  for(int i=0;i<prices.size();i++) {
   double c=((Number)prices.get(i).get("close")).doubleValue();
   var point=new LinkedHashMap<String,Object>();point.put("trading_date",prices.get(i).get("trading_date"));
   point.put("sma20",sma(prices,i,20));point.put("sma50",sma(prices,i,50));
   if(i>0) {
    double prev=((Number)prices.get(i-1).get("close")).doubleValue(),diff=c-prev;
    double r=c/prev-1;returns.add(r);point.put("daily_return",r);
    if(i<=14) {gain+=Math.max(diff,0)/14;loss+=Math.max(-diff,0)/14;}
    else {gain=(gain*13+Math.max(diff,0))/14;loss=(loss*13+Math.max(-diff,0))/14;}
   } else point.put("daily_return",null);
   point.put("rsi14",i<14?null:loss==0?(gain==0?50.0:100.0):100-100/(1+gain/loss));
   peak=Math.max(peak,c);drawdown=Math.min(drawdown,c/peak-1);points.add(point);
  }
  var out=new LinkedHashMap<String,Object>();out.put("points",points);out.put("observations",prices.size());
  Double vol=null,sharpe=null;
  if(returns.size()>=20) {
   double avg=returns.stream().mapToDouble(Double::doubleValue).average().orElse(0),variance=0;
   for(double r:returns) variance+=Math.pow(r-avg,2);
   double sd=Math.sqrt(variance/(returns.size()-1));vol=sd*Math.sqrt(252);if(sd>0) sharpe=avg/sd*Math.sqrt(252);
  }
  out.put("annualized_volatility",vol);out.put("sharpe_ratio",sharpe);out.put("maximum_drawdown",prices.size()>=2?drawdown:null);
  out.put("methodology","Unadjusted daily close; SMA 20/50; Wilder RSI 14; sample return standard deviation; 252 trading days; Sharpe risk-free rate 0. Splits and dividends are not corrected.");return out;
 }
 private Double sma(List<Map<String,Object>> prices,int i,int n) {
  if(i+1<n) return null;double sum=0;for(int j=i-n+1;j<=i;j++) sum+=((Number)prices.get(j).get("close")).doubleValue();return sum/n;
 }
}
