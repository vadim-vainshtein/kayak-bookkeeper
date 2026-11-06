package kz.kayak.bookkeeper.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kz.kayak.bookkeeper.api.dto.AllocatedPaymentWarningView;
import kz.kayak.bookkeeper.api.dto.FinalizedSubscriptionUpdateRequest;
import kz.kayak.bookkeeper.api.dto.MemberRequest;
import kz.kayak.bookkeeper.domain.Charge;
import kz.kayak.bookkeeper.domain.ChargeType;
import kz.kayak.bookkeeper.domain.Person;
import kz.kayak.bookkeeper.domain.Subscription;
import kz.kayak.bookkeeper.domain.SubscriptionMember;
import kz.kayak.bookkeeper.domain.TrainingSession;
import kz.kayak.bookkeeper.repository.ChargeRepository;
import kz.kayak.bookkeeper.repository.PaymentAllocationRepository;
import kz.kayak.bookkeeper.repository.PersonRepository;
import kz.kayak.bookkeeper.repository.SubscriptionMemberRepository;
import kz.kayak.bookkeeper.repository.TrainingSessionRepository;
import org.springframework.stereotype.Service;

import static kz.kayak.bookkeeper.service.EntityLookup.get;

/** Reconciles automatic charges and member snapshots without losing allocated payments. */
@Service
public class SubscriptionChargeReconciler {
    private final PersonRepository people;
    private final ChargeRepository charges;
    private final PaymentAllocationRepository allocations;
    private final TrainingSessionRepository sessions;
    private final SubscriptionMemberRepository members;

    public SubscriptionChargeReconciler(
            PersonRepository people,
            ChargeRepository charges,
            PaymentAllocationRepository allocations,
            TrainingSessionRepository sessions,
            SubscriptionMemberRepository members) {
        this.people = people;
        this.charges = charges;
        this.allocations = allocations;
        this.sessions = sessions;
        this.members = members;
    }

    List<AllocatedPaymentWarningView> allocatedPaymentWarnings(Subscription subscription, SubscriptionFeePlan plan) {
        List<Charge> affected = charges.findBySubscription(subscription).stream().filter(this::automaticSubscriptionCharge).filter(charge -> automaticChargeWillChange(charge, plan)).toList();
        if (affected.isEmpty()) return List.of();
        return allocations.findByChargeIn(affected).stream().map(allocation -> new AllocatedPaymentWarningView(allocation.getPayment().getId(), allocation.getPayment().getPaidAt(), allocation.getPayment().getAmount(), allocation.getAmount(), allocation.getCharge().getId(), allocation.getCharge().getDescription())).toList();
    }

    private boolean automaticChargeWillChange(Charge charge, SubscriptionFeePlan plan) {
        Long desired = plan.fees().get(new MemberChargeKey(charge.getPerson().getId(), charge.getType()));
        if (desired == null || desired == 0) return true;
        long amount = Math.max(desired, charges.allocatedFor(charge));
        return charge.getAmount() != amount || !charge.getChargedAt().equals(plan.chargedAt());
    }

    void applyFinalizedUpdate(Subscription subscription, FinalizedSubscriptionUpdateRequest request, SubscriptionFeePlan plan) {
        List<TrainingSession> previousSessions = sessions.findBySubscriptionInWithSubscription(List.of(subscription));
        for (TrainingSession session : previousSessions) {
            if (session.getAttendanceRevision() > 0 && !request.startsAt().contains(session.getStartsAt()))
                throw new DomainException("Нельзя удалить или перенести тренировку с сохранёнными посещениями. Сохраните её дату в расписании.");
        }
        Map<MemberChargeKey, Long> actualFees = new HashMap<>();
        Set<MemberChargeKey> consumed = new HashSet<>();
        for (Charge charge : charges.findBySubscription(subscription).stream().filter(this::automaticSubscriptionCharge).toList()) {
            MemberChargeKey key = new MemberChargeKey(charge.getPerson().getId(), charge.getType());
            Long desired = consumed.add(key) ? plan.fees().get(key) : null;
            long allocated = charges.allocatedFor(charge);
            if (desired == null || desired == 0) {
                if (allocated == 0) {
                    charges.delete(charge);
                    continue;
                }
                actualFees.put(key, charge.getAmount());
                continue;
            }
            long amount = Math.max(desired, allocated);
            charge.edit(amount, plan.chargedAt(), charge.getDescription());
            actualFees.put(key, amount);
        }
        for (Map.Entry<MemberChargeKey, Long> entry : plan.fees().entrySet()) {
            if (entry.getValue() == 0 || actualFees.containsKey(entry.getKey())) continue;
            MemberChargeKey key = entry.getKey();
            charges.save(new Charge(get(people, key.personId(), "Person"), subscription, key.type(), entry.getValue(), plan.chargedAt(), automaticChargeDescription(key.type())));
            actualFees.put(key, entry.getValue());
        }
        Map<java.time.LocalDateTime, TrainingSession> retainedSessions = new HashMap<>();
        for (TrainingSession session : previousSessions) {
            if (request.startsAt().contains(session.getStartsAt())) retainedSessions.put(session.getStartsAt(), session);
            else sessions.delete(session);
        }
        members.deleteBySubscription(subscription);
        subscription.updateMetadata(request.title(), request.description());
        subscription.updateCapacity(request.capacity());
        subscription.updatePoolRentalAmount(plan.poolRentalAmount());
        request.startsAt().stream().sorted().forEach(startsAt -> {
            TrainingSession session = retainedSessions.getOrDefault(startsAt, new TrainingSession(subscription, startsAt));
            session.snapshotPoolRental(plan.sessionRentals().get(startsAt));
            sessions.save(session);
        });
        for (int index = 0; index < request.members().size(); index++) {
            MemberRequest member = request.members().get(index);
            Person person = plan.people().get(index);
            SubscriptionMember saved = members.save(new SubscriptionMember(subscription, person, member.usesSkirt(), member.usesPaddle(), member.usesKayak()));
            saved.snapshotFees(actualFees.getOrDefault(new MemberChargeKey(person.getId(), ChargeType.POOL_SHARE), 0L), actualFees.getOrDefault(new MemberChargeKey(person.getId(), ChargeType.SKIRT_RENT), 0L), actualFees.getOrDefault(new MemberChargeKey(person.getId(), ChargeType.PADDLE_RENT), 0L), actualFees.getOrDefault(new MemberChargeKey(person.getId(), ChargeType.KAYAK_RENT), 0L));
        }
    }

    private boolean automaticSubscriptionCharge(Charge charge) {
        return charge.getType() != ChargeType.MANUAL;
    }

    private String automaticChargeDescription(ChargeType type) {
        return switch (type) {
            case POOL_SHARE -> "Доля аренды бассейна за абонемент";
            case SKIRT_RENT -> "Аренда юбки за абонемент";
            case PADDLE_RENT -> "Аренда весла за абонемент";
            case KAYAK_RENT -> "Аренда каяка за абонемент";
            default -> throw new IllegalArgumentException("Charge type does not belong to a subscription member fee");
        };
    }
}
