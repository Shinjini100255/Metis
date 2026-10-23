package io.metis.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public final class Requests {
 public record Name(@NotBlank @Size(max=100) String name) {}
 public record Portfolio(@NotBlank @Size(max=100) String name,@NotNull @Pattern(regexp="USD|INR") String baseCurrency) {}
 public record AssetId(@NotNull @Positive Long assetId) {}
 public record NewAsset(@NotNull @Pattern(regexp="[A-Z][A-Z0-9]{0,9}([.\\-][A-Z])?") String symbol,@NotBlank @Size(max=160) String companyName) {}
 public record Holding(@NotNull @Positive Long assetId,
  @NotNull @DecimalMin("0.000001") @DecimalMax("1000000000000") @Digits(integer=13,fraction=6) BigDecimal quantity,
  @NotNull @DecimalMin("0.000001") @DecimalMax("1000000000000") @Digits(integer=13,fraction=6) BigDecimal averagePurchasePrice) {}
 public record PageResult<T>(java.util.List<T> items,long total,int page,int size) {}
}
