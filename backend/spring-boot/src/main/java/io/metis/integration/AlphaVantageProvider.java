package io.metis.integration;
import com.fasterxml.jackson.databind.*;
import io.metis.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
@Component
public class AlphaVantageProvider implements MarketDataProvider {
 private final String key;private final int timeout,retries;private final ObjectMapper json;private final HttpClient client;
 public AlphaVantageProvider(@Value("${metis.provider-key}") String key,@Value("${metis.provider-timeout-seconds}") int timeout,@Value("${metis.provider-retries}") int retries,ObjectMapper json) {
  this.key=key;this.timeout=Math.clamp(timeout,2,30);this.retries=Math.clamp(retries,0,1);this.json=json;
  client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(this.timeout)).build();
 }
 public String name() {return "Alpha Vantage";}
 public boolean configured() {return !key.isBlank();}
 public List<Bar> fetch(String symbol) {
  if(!configured()) throw new ApiException(503,"PROVIDER_NOT_CONFIGURED","Configure ALPHA_VANTAGE_API_KEY on the backend to fetch historical daily data.");
  var uri=URI.create("https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&outputsize=compact&symbol="+URLEncoder.encode(symbol,StandardCharsets.UTF_8)+"&apikey="+URLEncoder.encode(key,StandardCharsets.UTF_8));
  for(int attempt=0;attempt<=retries;attempt++) {
   try {
    var response=client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(timeout)).GET().build(),HttpResponse.BodyHandlers.ofString());
    if(response.statusCode()!=200) throw new ApiException(502,"PROVIDER_HTTP","Historical provider returned HTTP "+response.statusCode()+". Retry later.");
    return parse(response.body());
   } catch(ApiException e) {throw e;}
   catch(InterruptedException e) {Thread.currentThread().interrupt();throw new ApiException(503,"PROVIDER_INTERRUPTED","Provider request interrupted.");}
   catch(Exception e) {if(attempt==retries) throw new ApiException(502,"PROVIDER_NETWORK","Historical provider could not be reached within the timeout.");}
  }
  throw new ApiException(502,"PROVIDER_NETWORK","Historical provider unavailable.");
 }
 public List<Bar> parse(String body) {
  try {
   var root=json.readTree(body);
   var zone=root.path("Meta Data").path("5. Time Zone").asText("");
   if(!zone.isEmpty() && !Set.of("US/Eastern","America/New_York").contains(zone)) throw new ApiException(502,"UNSUPPORTED_MARKET","This adapter is configured for US equities. The provider returned a different market timezone.");
   var series=root.get("Time Series (Daily)");
   if(series==null||!series.isObject()||series.isEmpty()) throw new ApiException(502,"PROVIDER_UNAVAILABLE","Provider returned no daily records. The symbol, API key, quota, or plan may be unavailable.");
   if(series.size()>100) throw new ApiException(502,"PROVIDER_INVALID","Provider exceeded the compact history limit.");
   var out=new ArrayList<Bar>();
   for(var entry:series.properties()) {
    var v=entry.getValue();LocalDate date=LocalDate.parse(entry.getKey());
    // The provider encodes decimal and integer values as JSON strings.
    var o=new java.math.BigDecimal(v.required("1. open").asText());var h=new java.math.BigDecimal(v.required("2. high").asText());var l=new java.math.BigDecimal(v.required("3. low").asText());var c=new java.math.BigDecimal(v.required("4. close").asText());
    long volume=Long.parseLong(v.required("5. volume").asText());
    if(o.signum()<=0||c.signum()<=0||l.signum()<=0||volume<0||h.compareTo(o.max(c))<0||l.compareTo(o.min(c))>0||h.compareTo(l)<0||date.isAfter(LocalDate.now(ZoneOffset.UTC))) throw new IllegalArgumentException();
    out.add(new Bar(date,o,h,l,c,volume,null));
   }
   out.sort(Comparator.comparing(Bar::date));return out;
  } catch(ApiException e) {throw e;}catch(Exception e) {throw new ApiException(502,"PROVIDER_INVALID","Historical provider returned malformed records. No prices were saved.");}
 }
}
