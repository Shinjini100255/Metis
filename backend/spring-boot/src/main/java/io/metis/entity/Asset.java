package io.metis.entity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
@Entity @Table(name="assets")
public class Asset {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(nullable=false,unique=true,length=15) public String symbol;
 @Column(nullable=false,length=160) public String companyName;
 @Column(nullable=false,length=40) public String exchange;
 @JdbcTypeCode(SqlTypes.CHAR)
 @Column(nullable=false,length=3,columnDefinition="char(3)") public String currency;
 @Column(nullable=false,length=20) public String assetType="STOCK";
 @Column(nullable=false) public Instant createdAt=Instant.now();
 public Asset() {}
 public Asset(String symbol,String name) { this.symbol=symbol;this.companyName=name;this.exchange="US (unverified venue)";this.currency="USD"; }
}
