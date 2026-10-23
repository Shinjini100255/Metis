package io.metis;
import io.metis.integration.MlClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
class MlClientTest {
 @Test void datesUseTheApplicationIsoJsonContractForPython() throws Exception {
  var json=Jackson2ObjectMapperBuilder.json().featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
  var received=new AtomicReference<String>();
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/data/plan",exchange->{
   received.set(new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
   byte[] body="{\"missing_sessions\":0}".getBytes();exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
  });
  server.start();
  try {
   var client=new MlClient("http://127.0.0.1:"+server.getAddress().getPort(),json);
   client.post("/data/plan",Map.of("start",LocalDate.of(2024,1,2),"end",LocalDate.of(2024,1,3),"existing",List.of(java.sql.Date.valueOf("2024-01-02"))));
   var sent=json.readTree(received.get());assertEquals("2024-01-02",sent.get("start").asText());assertEquals("2024-01-02",sent.get("existing").get(0).asText());
  } finally {server.stop(0);}
 }
}
