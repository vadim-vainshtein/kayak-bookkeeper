package kz.kayak.bookkeeper.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity @Table(name = "pricing_rates")
public class PricingRate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RateCode code;
    @Column(nullable = false) private long amount;
    @Column(nullable = false) private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    protected PricingRate() { }
    public PricingRate(RateCode code, long amount, LocalDate effectiveFrom, LocalDate effectiveTo) { this.code=code; this.amount=amount; this.effectiveFrom=effectiveFrom; this.effectiveTo=effectiveTo; }
    public Long getId() { return id; } public RateCode getCode() { return code; } public long getAmount() { return amount; } public LocalDate getEffectiveFrom() { return effectiveFrom; } public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
}
