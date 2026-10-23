package io.metis;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @Testcontainers(disabledWithoutDocker=true)
class PersistenceIT {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17.6-alpine");
 @DynamicPropertySource static void configure(DynamicPropertyRegistry r) {r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);r.add("metis.provider-key",()->"");}
 @Autowired MockMvc mvc;@Autowired JdbcTemplate db;@Autowired ObjectMapper json;
 @Test void migrationsAndReferenceOnlySeeds() throws Exception {
  assertEquals(2,db.queryForObject("select count(*) from flyway_schema_history where success=true",Integer.class));
  assertEquals(13,db.queryForObject("select count(*) from information_schema.tables where table_schema='public' and table_name<>'flyway_schema_history'",Integer.class));
  assertTrue(db.queryForObject("select count(*) from assets",Integer.class)>=10);assertEquals(0,db.queryForObject("select count(*) from historical_prices",Integer.class));
  mvc.perform(get("/api/v1/health")).andExpect(status().isOk()).andExpect(jsonPath("$.postgres.status").value("UP"));
  mvc.perform(get("/api/v1/assets/search?q=AAPL")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].symbol").value("AAPL"));
 }
 @Test void watchlistAndPortfolioRoundTripPersistsAcrossRequests() throws Exception {
  long asset=db.queryForObject("select id from assets where symbol='AAPL'",Long.class);
  var w=mvc.perform(post("/api/v1/watchlists").contentType("application/json").content("{\"name\":\"Integration list\"}")).andExpect(status().isCreated()).andReturn();
  long wid=json.readTree(w.getResponse().getContentAsString()).get("id").asLong();
  mvc.perform(post("/api/v1/watchlists/"+wid+"/assets").contentType("application/json").content("{\"asset_id\":"+asset+"}")).andExpect(status().isNoContent());
  assertEquals(1,db.queryForObject("select count(*) from watchlist_items where watchlist_id=?",Integer.class,wid));
  mvc.perform(delete("/api/v1/watchlists/"+wid+"/assets/"+asset)).andExpect(status().isNoContent());
  var result=mvc.perform(post("/api/v1/portfolios").contentType("application/json").content("{\"name\":\"Paper integration\",\"base_currency\":\"USD\"}")).andExpect(status().isCreated()).andReturn();
  long pid=json.readTree(result.getResponse().getContentAsString()).get("id").asLong();String body="{\"asset_id\":"+asset+",\"quantity\":2,\"average_purchase_price\":100.10}";
  mvc.perform(post("/api/v1/portfolios/"+pid+"/holdings").contentType("application/json").content(body)).andExpect(status().isNoContent());
  mvc.perform(get("/api/v1/portfolios/"+pid)).andExpect(jsonPath("$.holdings[0].quantity").value(2)).andExpect(jsonPath("$.invested_capital").value(200.20)).andExpect(jsonPath("$.market_value").isEmpty());
  long hid=db.queryForObject("select id from portfolio_holdings where portfolio_id=?",Long.class,pid);
  mvc.perform(put("/api/v1/portfolios/"+pid+"/holdings/"+hid).contentType("application/json").content(body.replace("\"quantity\":2","\"quantity\":3"))).andExpect(status().isNoContent());
  mvc.perform(get("/api/v1/portfolios/"+pid)).andExpect(jsonPath("$.holdings[0].quantity").value(3));
  mvc.perform(delete("/api/v1/portfolios/"+pid+"/holdings/"+hid)).andExpect(status().isNoContent());
  mvc.perform(delete("/api/v1/portfolios/"+pid)).andExpect(status().isNoContent());
  mvc.perform(get("/api/v1/portfolios/"+pid)).andExpect(status().isNotFound());
 }
 @Test void constraintsAndSwagger() throws Exception {
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.update("insert into assets(symbol,company_name,exchange,currency) values('AAPL','Duplicate','NASDAQ','USD')"));
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.update("insert into portfolio_holdings(portfolio_id,asset_id,quantity,average_purchase_price) values(-1,-1,1,1)"));
  mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.paths['/api/v1/portfolios']").exists());
  mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
 }
}
