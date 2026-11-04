package kz.kayak.bookkeeper.service;

import jakarta.transaction.Transactional;
import java.util.NoSuchElementException;
import kz.kayak.bookkeeper.api.dto.ChargeEditRequest;
import kz.kayak.bookkeeper.api.dto.ChargeView;
import kz.kayak.bookkeeper.api.dto.ManualChargeRequest;
import kz.kayak.bookkeeper.domain.Charge;
import kz.kayak.bookkeeper.domain.ChargeType;
import kz.kayak.bookkeeper.domain.Person;
import kz.kayak.bookkeeper.domain.Subscription;
import kz.kayak.bookkeeper.repository.ChargeRepository;
import kz.kayak.bookkeeper.repository.PersonRepository;
import kz.kayak.bookkeeper.repository.SubscriptionMemberRepository;
import kz.kayak.bookkeeper.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;

import static kz.kayak.bookkeeper.service.EntityLookup.get;

/** Creates, edits and deletes charges while protecting allocations and fee snapshots. */
@Service
public class ChargeService {
    private final PersonRepository people;
    private final SubscriptionRepository subscriptions;
    private final ChargeRepository charges;
    private final SubscriptionMemberRepository members;
    private final LedgerViewMapper views;

    public ChargeService(
            PersonRepository people,
            SubscriptionRepository subscriptions,
            ChargeRepository charges,
            SubscriptionMemberRepository members,
            LedgerViewMapper views) {
        this.people = people;
        this.subscriptions = subscriptions;
        this.charges = charges;
        this.members = members;
        this.views = views;
    }

    @Transactional
    public ChargeView createManualCharge(ManualChargeRequest r) {
        Person p = get(people, r.personId(), "Person");
        Subscription s = r.subscriptionId() == null ? null : get(subscriptions, r.subscriptionId(), "Subscription");
        return views.chargeView(charges.save(new Charge(p, s, null, ChargeType.MANUAL, r.amount(), r.chargedAt(), r.description())));
    }

    @Transactional
    public ChargeView editCharge(long id, ChargeEditRequest r) {
        Charge c = lockedCharge(id);
        if (charges.allocatedFor(c) > r.amount())
            throw new DomainException("Charge amount cannot be below allocated payment amount");
        Subscription s = c.getSubscription();
        if (r.updateSubscription() || r.subscriptionId() != null) s = r.subscriptionId() == null ? null : get(subscriptions, r.subscriptionId(), "Subscription");
        c.edit(r.amount(), r.chargedAt(), r.description(), s);
        syncAutomaticFee(c, c.getAmount());
        return views.chargeView(c);
    }

    @Transactional
    public void deleteCharge(long id) {
        Charge c = lockedCharge(id);
        if (charges.allocatedFor(c) > 0)
            throw new DomainException("Нельзя удалить начисление с распределённой оплатой. Сначала отмените распределение в окне редактирования оплаты.");
        syncAutomaticFee(c, 0);
        charges.delete(c);
    }

    private Charge lockedCharge(long id) {
        return charges.findWithLockById(id).orElseThrow(() -> new NoSuchElementException("Charge " + id + " was not found"));
    }

    private void syncAutomaticFee(Charge c, long amount) {
        if (c.getSubscription() == null || c.getType() == ChargeType.MANUAL || c.getType() == ChargeType.DROP_IN)
            return;
        members.findBySubscriptionAndPerson(c.getSubscription(), c.getPerson()).ifPresent(member -> member.editAutomaticFee(c.getType(), amount));
    }
}
