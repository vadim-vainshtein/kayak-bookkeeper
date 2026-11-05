package kz.kayak.bookkeeper.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import kz.kayak.bookkeeper.api.dto.FinalizedSubscriptionUpdateRequest;
import kz.kayak.bookkeeper.api.dto.MemberRequest;
import kz.kayak.bookkeeper.domain.ChargeType;
import kz.kayak.bookkeeper.domain.Person;
import kz.kayak.bookkeeper.domain.RateCode;
import org.springframework.stereotype.Service;

/** Calculates the desired fees for a replacement finalized subscription schedule. */
@Service
public class SubscriptionFeePlanner {
    private final SubscriptionMemberSelection memberSelection;
    private final PricingRateService pricing;

    public SubscriptionFeePlanner(SubscriptionMemberSelection memberSelection, PricingRateService pricing) {
        this.memberSelection = memberSelection;
        this.pricing = pricing;
    }

    SubscriptionFeePlan plan(FinalizedSubscriptionUpdateRequest request) {
        if (new HashSet<>(request.startsAt()).size() != request.startsAt().size())
            throw new DomainException("Training dates must be distinct");
        List<Person> selectedPeople = memberSelection.validateMembers(request.members(), request.capacity());
        List<LocalDateTime> startsAt = request.startsAt().stream().sorted().toList();
        Map<LocalDateTime, Long> sessionRentals = new HashMap<>();
        startsAt.forEach(value -> sessionRentals.put(value, pricing.applicableAmount(RateCode.POOL_RENTAL, value.toLocalDate())));
        long poolRentalAmount = sessionRentals.values().stream().mapToLong(Long::longValue).sum();
        long poolShare = startsAt.stream().mapToLong(value -> ceilDiv(sessionRentals.get(value), selectedPeople.size())).sum();
        Map<MemberChargeKey, Long> fees = new HashMap<>();
        for (int index = 0; index < request.members().size(); index++) {
            MemberRequest member = request.members().get(index);
            Long personId = selectedPeople.get(index).getId();
            fees.put(new MemberChargeKey(personId, ChargeType.POOL_SHARE), poolShare);
            fees.put(new MemberChargeKey(personId, ChargeType.SKIRT_RENT), member.usesSkirt() ? pricing.sumRateAt(startsAt, RateCode.SKIRT_RENTAL) : 0);
            fees.put(new MemberChargeKey(personId, ChargeType.PADDLE_RENT), member.usesPaddle() ? pricing.sumRateAt(startsAt, RateCode.PADDLE_RENTAL) : 0);
            fees.put(new MemberChargeKey(personId, ChargeType.KAYAK_RENT), member.usesKayak() ? pricing.sumRateAt(startsAt, RateCode.KAYAK_RENTAL) : 0);
        }
        return new SubscriptionFeePlan(selectedPeople, fees, poolRentalAmount, Map.copyOf(sessionRentals), startsAt.getFirst().toLocalDate());
    }

    private static long ceilDiv(long v, long d) {
        return (v + d - 1) / d;
    }
}
