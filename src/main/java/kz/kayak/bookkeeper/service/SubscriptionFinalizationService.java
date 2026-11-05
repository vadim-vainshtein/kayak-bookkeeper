package kz.kayak.bookkeeper.service;

import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;
import kz.kayak.bookkeeper.api.dto.SubscriptionView;
import kz.kayak.bookkeeper.domain.Charge;
import kz.kayak.bookkeeper.domain.ChargeType;
import kz.kayak.bookkeeper.domain.RateCode;
import kz.kayak.bookkeeper.domain.Subscription;
import kz.kayak.bookkeeper.domain.SubscriptionMember;
import kz.kayak.bookkeeper.domain.TrainingSession;
import kz.kayak.bookkeeper.repository.ChargeRepository;
import kz.kayak.bookkeeper.repository.SubscriptionMemberRepository;
import org.springframework.stereotype.Service;

/** Snapshots per-session fees and creates the initial subscription charges atomically. */
@Service
public class SubscriptionFinalizationService {
    private final SubscriptionMemberRepository members;
    private final ChargeRepository charges;
    private final SubscriptionAccess access;
    private final PricingRateService pricing;
    private final SubscriptionQueryService views;

    public SubscriptionFinalizationService(
            SubscriptionMemberRepository members,
            ChargeRepository charges,
            SubscriptionAccess access,
            PricingRateService pricing,
            SubscriptionQueryService views) {
        this.members = members;
        this.charges = charges;
        this.access = access;
        this.pricing = pricing;
        this.views = views;
    }

    @Transactional
    public SubscriptionView finalizeSubscription(long id) {
        Subscription s = access.lockedSubscription(id);
        access.ensureDraft(s);
        List<SubscriptionMember> group = members.findBySubscription(s);
        List<TrainingSession> ss = access.sessionsFor(s);
        if (group.isEmpty()) throw new DomainException("Cannot finalize a subscription without members");
        if (ss.isEmpty()) throw new DomainException("Cannot finalize a subscription without sessions");
        ss.forEach(session -> session.snapshotPoolRental(pricing.applicableAmount(RateCode.POOL_RENTAL, session.getStartsAt().toLocalDate())));
        long poolRentalTotal = ss.stream().mapToLong(TrainingSession::getPoolRentalAmount).sum();
        long poolShare = ss.stream().mapToLong(x -> ceilDiv(x.getPoolRentalAmount(), group.size())).sum();
        s.finalizeWith(poolRentalTotal);
        LocalDate chargedAt = ss.getFirst().getStartsAt().toLocalDate();
        for (SubscriptionMember member : group) {
            long skirt = member.isUsesSkirt() ? sumRate(ss, RateCode.SKIRT_RENTAL) : 0, paddle = member.isUsesPaddle() ? sumRate(ss, RateCode.PADDLE_RENTAL) : 0, kayak = member.isUsesKayak() ? sumRate(ss, RateCode.KAYAK_RENTAL) : 0;
            member.snapshotFees(poolShare, skirt, paddle, kayak);
            if (poolShare > 0)
                charges.save(new Charge(member.getPerson(), s, ChargeType.POOL_SHARE, poolShare, chargedAt, "Доля аренды бассейна за абонемент"));
            if (skirt > 0)
                charges.save(new Charge(member.getPerson(), s, ChargeType.SKIRT_RENT, skirt, chargedAt, "Аренда юбки за абонемент"));
            if (paddle > 0)
                charges.save(new Charge(member.getPerson(), s, ChargeType.PADDLE_RENT, paddle, chargedAt, "Аренда весла за абонемент"));
            if (kayak > 0)
                charges.save(new Charge(member.getPerson(), s, ChargeType.KAYAK_RENT, kayak, chargedAt, "Аренда каяка за абонемент"));
        }
        return views.subscriptionView(s);
    }

    private static long ceilDiv(long v, long d) {
        return (v + d - 1) / d;
    }

    private long sumRate(List<TrainingSession> ss, RateCode c) {
        return ss.stream().mapToLong(s -> pricing.applicableAmount(c, s.getStartsAt().toLocalDate())).sum();
    }
}
