package kz.kayak.bookkeeper.service;

import kz.kayak.bookkeeper.api.dto.MemberView;
import kz.kayak.bookkeeper.api.dto.PersonView;
import kz.kayak.bookkeeper.api.dto.RateView;
import kz.kayak.bookkeeper.domain.Person;
import kz.kayak.bookkeeper.domain.PricingRate;
import kz.kayak.bookkeeper.domain.SubscriptionMember;

final class EntityViews {
    private EntityViews() {}

    static PersonView personView(Person p) {
        return new PersonView(p.getId(), p.getFullName(), p.getPhone(), p.getNote(), p.isActive());
    }

    static RateView rateView(PricingRate r) {
        return new RateView(r.getId(), r.getCode(), r.getAmount(), r.getEffectiveFrom(), r.getEffectiveTo());
    }

    static MemberView memberView(SubscriptionMember m) {
        return new MemberView(m.getId(), m.getPerson().getId(), m.getPerson().getFullName(), m.isUsesSkirt(), m.isUsesPaddle(), m.isUsesKayak(), m.getPoolShare(), m.getSkirtFee(), m.getPaddleFee(), m.getKayakFee());
    }

}
