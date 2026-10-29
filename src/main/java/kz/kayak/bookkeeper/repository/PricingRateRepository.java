package kz.kayak.bookkeeper.repository;
import kz.kayak.bookkeeper.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.*;
public interface PricingRateRepository extends JpaRepository<PricingRate, Long> {
    List<PricingRate> findByCode(RateCode code);
    @org.springframework.data.jpa.repository.Query("select r from PricingRate r where r.code=:code and r.effectiveFrom <= :date and (r.effectiveTo is null or r.effectiveTo >= :date) order by r.effectiveFrom desc")
    List<PricingRate> applicable(RateCode code, LocalDate date);
}
