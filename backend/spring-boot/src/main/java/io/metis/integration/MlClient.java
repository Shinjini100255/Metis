package io.metis.integration;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.time.Duration;
@Component
public class MlClient {
 @JsonIgnoreProperties(ignoreUnknown=true) public record ModelStatus(boolean available,String status,String model_version,String message) {}
 @JsonIgnoreProperties(ignoreUnknown=true) public record Health(String status,String service,String version) {}
 private final RestClient client;private final com.fasterxml.jackson.databind.ObjectMapper json;
 public MlClient(@Value("${metis.ml-url}") String url,com.fasterxml.jackson.databind.ObjectMapper json) {
  this.json=json;
  var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
  var factory=new JdkClientHttpRequestFactory(http);factory.setReadTimeout(Duration.ofSeconds(20));
  client=RestClient.builder().baseUrl(url).requestFactory(factory).messageConverters(converters->converters.forEach(converter->{
   if(converter instanceof org.springframework.http.converter.json.MappingJackson2HttpMessageConverter jackson) jackson.setObjectMapper(json);
  })).build();
 }
 @SuppressWarnings("unchecked") public java.util.Map<String,Object> post(String path,Object body) {
  try {return client.post().uri(path).body(body).retrieve().body(java.util.Map.class);}
  catch(org.springframework.web.client.RestClientResponseException e) {
   try {var node=json.readTree(e.getResponseBodyAsString());throw new io.metis.exception.ApiException(e.getStatusCode().value(),node.path("code").asText("ML_VALIDATION"),node.path("message").asText("ML request was rejected."));}
   catch(io.metis.exception.ApiException a) {throw a;}catch(Exception ignored) {throw new io.metis.exception.ApiException(502,"ML_RESPONSE","Model service returned an invalid response.");}
  } catch(org.springframework.web.client.RestClientException e) {throw new io.metis.exception.ApiException(503,"ML_UNAVAILABLE","Model service is unavailable. Stored data remains available.");}
 }
 @SuppressWarnings("unchecked") public java.util.Map<String,Object> get(String path) {return client.get().uri(path).retrieve().body(java.util.Map.class);}
 public Health health() {return client.get().uri("/health").retrieve().body(Health.class);}
 public ModelStatus status() {return client.get().uri("/model-status").retrieve().body(ModelStatus.class);}
}
