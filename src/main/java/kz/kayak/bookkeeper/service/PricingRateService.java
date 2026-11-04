package kz.kayak.bookkeeper.service;

import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import kz.kayak.bookkeeper.api.dto.RateEndRequest;
import kz.kayak.bookkeeper.api.dto.RateRequest;
import kz.kayak.bookkeeper.api.dto.RateView;
import kz.kayak.bookkeeper.domain.PricingRate;
import kz.kayak.bookkeeper.domain.RateCode;
import kz.kayak.bookkeeper.repository.PricingRateRepository;
import org.springframework.stereotype.Service;

import static kz.kayak.bookkeeper.service.EntityLookup.get;
import static kz.kayak.bookkeeper.service.EntityViews.rateView;

/** Maintains effective rate periods and resolves prices on training dates. */
@Service
public class PricingRateService {
    private final PricingRateRepository rates;

    public PricingRateService(PricingRateRepository rates) {
        this.rates = rates;
    }

    private static boolean overlaps(LocalDate af, LocalDate at, LocalDate bf, LocalDate bt) {
        return (at == null || !at.isBefore(bf)) && (bt == null || !bt.isBefore(af));
    }

    public RateView createRate(RateRequest r) {
        if (r.effectiveTo() != null && r.effectiveTo().isBefore(r.effectiveFrom()))
            throw new DomainException("effectiveTo cannot be before effectiveFrom");
        for (PricingRate existing : rates.findByCode(r.code()))
            if (overlaps(r.effectiveFrom(), r.effectiveTo(), existing.getEffectiveFrom(), existing.getEffectiveTo()))
                throw new DomainException("Pricing-rate periods for one code must not overlap");
        return rateView(rates.save(new PricingRate(r.code(), r.amount(), r.effectiveFrom(), r.effectiveTo())));
    }

    public List<RateView> listRates() {
        return rates.findAll().stream().map(EntityViews::rateView).toList();
    }

    @Transactional
    public void deleteRate(long id) {
        rates.delete(get(rates, id, "Pricing rate"));
    }

    @Transactional
    public RateView endRate(long id, RateEndRequest r) {
        PricingRate rate = get(rates, id, "Pricing rate");
        if (r.effectiveTo().isBefore(rate.getEffectiveFrom()))
            throw new DomainException("effectiveTo cannot be before effectiveFrom");
        for (PricingRate other : rates.findByCode(rate.getCode()))
            if (!other.getId().equals(rate.getId()) && overlaps(rate.getEffectiveFrom(), r.effectiveTo(), other.getEffectiveFrom(), other.getEffectiveTo()))
                throw new DomainException("Pricing-rate periods for one code must not overlap");
        rate.setEffectiveTo(r.effectiveTo());
        return rateView(rate);
    }

    public long applicableAmount(RateCode c, LocalDate d) {
        return rates.applicable(c, d).stream().findFirst().orElseThrow(() -> new DomainException("No active " + c + " rate on " + d)).getAmount();
    }

    public long sumRateAt(List<LocalDateTime> startsAt, RateCode code) {
        return startsAt.stream().mapToLong(value -> applicableAmount(code, value.toLocalDate())).sum();
    }
}
