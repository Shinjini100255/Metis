package io.metis;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class InfrastructureTest {
 @Test void composeYamlReferencesExistingContextsAndInternalDependencies() throws Exception {
  Path root=Path.of(System.getProperty("basedir"),"../..").normalize();
  Map<String,Object> compose=new Yaml().load(Files.readString(root.resolve("docker-compose.yml")));
  Map<String,Map<String,Object>> services=(Map<String,Map<String,Object>>)compose.get("services");
  assertEquals(Set.of("postgres","spring-backend","ml-api","frontend"),services.keySet());
  assertFalse(services.get("postgres").containsKey("ports"));
  for(var entry:services.entrySet()) {
   var service=entry.getValue();assertNotNull(service.get("healthcheck"));assertNotNull(service.get("mem_limit"));assertNotNull(service.get("logging"));
   for(String port:(List<String>)service.getOrDefault("ports",List.of())) assertTrue(port.startsWith("127.0.0.1:"),"Development ports must bind to loopback");
   if(service.get("depends_on") instanceof Map<?,?> deps) assertTrue(services.keySet().containsAll(deps.keySet()));
   if(service.get("build") instanceof Map<?,?> build) {
    Path context=root.resolve((String)build.get("context")).normalize();
    Object file=build.get("dockerfile");assertTrue(Files.isRegularFile(context.resolve(file==null?"Dockerfile":(String)file)));
   }
  }
  assertTrue(((Map<?,?>)compose.get("volumes")).containsKey("postgres_data"));
  assertTrue(Files.isRegularFile(root.resolve("frontend/package-lock.json")));
  assertTrue(Files.isRegularFile(root.resolve("services/ml-api/requirements.lock.txt")));
  new Yaml().load(Files.readString(root.resolve(".github/workflows/ci.yml")));
  new Yaml().load(Files.readString(root.resolve("backend/spring-boot/src/main/resources/application.yml")));
  new Yaml().load(Files.readString(root.resolve("infrastructure/docker/compose.native.yml")));
 }
}
