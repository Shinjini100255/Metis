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
 private final RestClient client;
 public MlClient(@Value("${metis.ml-url}") String url) {
  var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
  var factory=new JdkClientHttpRequestFactory(http);factory.setReadTimeout(Duration.ofSeconds(3));
  client=RestClient.builder().baseUrl(url).requestFactory(factory).build();
 }
 public Health health() {return client.get().uri("/health").retrieve().body(Health.class);}
 public ModelStatus status() {return client.get().uri("/model-status").retrieve().body(ModelStatus.class);}
}
