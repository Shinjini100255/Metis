package io.metis.controller;
import io.metis.dto.Requests.*;
import io.metis.service.*;
import io.metis.repository.Store;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;
@RestController @RequestMapping("/api/v1") @Validated
public class ApiController {
 private final AssetService assets;private final PriceService prices;private final IndicatorService indicators;private final WatchlistService watchlists;private final PortfolioService portfolios;private final IngestionService ingestion;private final SystemService system;private final CatalogService catalog;
 public ApiController(AssetService assets,PriceService prices,IndicatorService indicators,WatchlistService watchlists,PortfolioService portfolios,IngestionService ingestion,SystemService system,CatalogService catalog) {this.assets=assets;this.prices=prices;this.indicators=indicators;this.watchlists=watchlists;this.portfolios=portfolios;this.ingestion=ingestion;this.system=system;this.catalog=catalog;}
 @GetMapping("/health") public Object health() {return system.status();}
 @GetMapping({"/assets","/assets/search"}) public Object assets(@RequestParam(defaultValue="") @Size(max=80) String q,@RequestParam(defaultValue="0") @Min(0) @Max(10000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {return assets.search(q,page,size);}
 @PostMapping("/assets") @ResponseStatus(org.springframework.http.HttpStatus.CREATED) public Object addAsset(@Valid @RequestBody NewAsset r) {return assets.add(r);}
 @GetMapping("/assets/{id}") public Object asset(@PathVariable @Positive long id) {return assets.get(id);}
 @GetMapping("/assets/{id}/prices") public Object prices(@PathVariable @Positive long id,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,@RequestParam(defaultValue="0") @Min(0) @Max(10000) int page,@RequestParam(defaultValue="100") @Min(1) @Max(1260) int size) {var end=to==null?LocalDate.now(ZoneOffset.UTC):to;return prices.prices(id,from==null?end.minusYears(1):from,end,page,size);}
 @GetMapping("/assets/{id}/indicators") public Object indicators(@PathVariable @Positive long id,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to) {var end=to==null?LocalDate.now(ZoneOffset.UTC):to;return indicators.calculate(prices.history(id,from==null?end.minusYears(1):from,end));}
 @GetMapping("/quotes") public Object quotes() {return prices.quotes();}
 @PostMapping("/assets/{id}/ingest") public Object ingest(@PathVariable @Positive long id,@RequestHeader(value="X-Confirm-Live-Ingestion",defaultValue="false") boolean confirm) {if(!confirm) throw new io.metis.exception.ApiException(409,"CONFIRMATION_REQUIRED","Live ingestion requires explicit confirmation; use the dry-run plan first.");return ingestion.ingest(id);}
 public record Plan(@NotEmpty @Size(max=20) List<@Positive Long> assetIds,@NotNull LocalDate from,@NotNull LocalDate to) {}
 public record LocalImport(@NotEmpty @Size(max=5000) List<Map<String,Object>> bars,boolean developmentOnly) {}
 @PostMapping("/ingestion/plan") public Object plan(@Valid @RequestBody Plan r) {return ingestion.plan(r.assetIds(),r.from(),r.to());}
 @GetMapping("/ingestion/protection") public Object protection() {return ingestion.protection();}
 @PostMapping("/assets/{id}/import") public Object localImport(@PathVariable @Positive long id,@Valid @RequestBody LocalImport r) {return ingestion.importLocal(id,r.bars(),r.developmentOnly());}
 @GetMapping("/watchlists") public Object watchlists() {return watchlists.list();}
 @PostMapping("/watchlists") @ResponseStatus(org.springframework.http.HttpStatus.CREATED) public Object createWatchlist(@Valid @RequestBody Name r) {return watchlists.create(r.name());}
 @PostMapping("/watchlists/{id}/assets") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void addWatchlistAsset(@PathVariable @Positive long id,@Valid @RequestBody AssetId r) {watchlists.add(id,r.assetId());}
 @DeleteMapping("/watchlists/{id}/assets/{assetId}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void removeWatchlistAsset(@PathVariable @Positive long id,@PathVariable @Positive long assetId) {watchlists.remove(id,assetId);}
 @GetMapping("/portfolios") public Object portfolios() {return portfolios.list();}
 @PostMapping("/portfolios") @ResponseStatus(org.springframework.http.HttpStatus.CREATED) public Object createPortfolio(@Valid @RequestBody Portfolio r) {return portfolios.create(r);}
 @GetMapping("/portfolios/{id}") public Object portfolio(@PathVariable @Positive long id) {return portfolios.get(id);}
 @DeleteMapping("/portfolios/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void deletePortfolio(@PathVariable @Positive long id) {portfolios.delete(id);}
 @PostMapping("/portfolios/{id}/holdings") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void addHolding(@PathVariable @Positive long id,@Valid @RequestBody Holding r) {portfolios.add(id,r);}
 @PutMapping("/portfolios/{id}/holdings/{holdingId}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void updateHolding(@PathVariable @Positive long id,@PathVariable @Positive long holdingId,@Valid @RequestBody Holding r) {portfolios.update(id,holdingId,r);}
 @DeleteMapping("/portfolios/{id}/holdings/{holdingId}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void deleteHolding(@PathVariable @Positive long id,@PathVariable @Positive long holdingId) {portfolios.remove(id,holdingId);}
 @GetMapping("/predictions") public Object predictions(@RequestParam(defaultValue="0") @Min(0) @Max(10000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size,@RequestParam(required=false) @Positive Long assetId) {return catalog.predictions(assetId,page,size);}
 @GetMapping("/models") public Object models() {return catalog.models();}
 @GetMapping("/models/operations") public Object modelOperations() {return catalog.operations();}
 @GetMapping("/models/status") public Object modelStatus() {return system.modelStatus();}
 @GetMapping("/strategies") public Object strategies() {return catalog.strategies();}
 @GetMapping("/backtests") public Object backtests() {return catalog.backtests();}
 @GetMapping("/system/status") public Object status() {return system.status();}
 @GetMapping("/system/ingestion") public Object ingestion() {return catalog.ingestion();}
}
