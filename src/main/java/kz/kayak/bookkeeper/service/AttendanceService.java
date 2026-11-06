package kz.kayak.bookkeeper.service;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import kz.kayak.bookkeeper.api.dto.*;
import kz.kayak.bookkeeper.domain.*;
import kz.kayak.bookkeeper.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static kz.kayak.bookkeeper.service.EntityLookup.get;

/** Saves the complete roster and its guest charges atomically under the subscription lock. */
@Service
public class AttendanceService {
    private final TrainingSessionRepository sessions;
    private final SessionAttendanceRepository attendance;
    private final SubscriptionMemberRepository members;
    private final PersonRepository people;
    private final ChargeRepository charges;
    private final PricingRateRepository rates;
    private final SubscriptionAccess access;
    private final Clock clock;
    private final EntityManager entityManager;

    public AttendanceService(TrainingSessionRepository sessions, SessionAttendanceRepository attendance,
            SubscriptionMemberRepository members, PersonRepository people, ChargeRepository charges,
            PricingRateRepository rates, SubscriptionAccess access, Clock clock, EntityManager entityManager) {
        this.sessions = sessions;
        this.attendance = attendance;
        this.members = members;
        this.people = people;
        this.charges = charges;
        this.rates = rates;
        this.access = access;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    public boolean available(TrainingSession session) {
        return session.getSubscription().getStatus() != SubscriptionStatus.DRAFT
                && !session.getStartsAt().isAfter(LocalDateTime.now(clock));
    }

    @Transactional(readOnly = true)
    public AttendanceView getAttendance(long sessionId) {
        return view(get(sessions, sessionId, "Training session"));
    }

    @Transactional
    public AttendanceView saveAttendance(long sessionId, AttendanceUpdateRequest request) {
        Long subscriptionId = sessions.findSubscriptionIdById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Training session " + sessionId + " was not found"));
        // The same lock serializes roster writes and finalized subscription edits.
        Subscription subscription = access.lockedSubscription(subscriptionId);
        // An earlier reader may have waited for another writer: discard its pre-lock state.
        entityManager.refresh(subscription);
        // Load the session only after taking the lock: an unrecorded session might have
        // been removed by a finalized edit while this request waited for that lock.
        TrainingSession session = get(sessions, sessionId, "Training session");
        entityManager.refresh(session);
        if (!available(session)) throw new DomainException("Посещения доступны после начала тренировки рассчитанного абонемента.");
        if (request.revision() == null || request.revision() != session.getAttendanceRevision())
            throw new DomainException("Посещения уже изменены. Закройте окно и откройте его снова, чтобы загрузить актуальный список.");
        if (request.memberIds() == null || request.guests() == null)
            throw new DomainException("Укажите участников и гостей тренировки.");

        Map<Long, SessionAttendance> existing = new HashMap<>();
        for (SessionAttendance entry : attendance.findBySession(session)) existing.put(entry.getPerson().getId(), entry);
        // Lock charges in stable order, also protecting against concurrent payment allocation.
        existing.values().stream().map(SessionAttendance::getCharge).filter(Objects::nonNull)
                .sorted(Comparator.comparing(Charge::getId)).forEach(charge -> charges.findWithLockById(charge.getId()).orElseThrow());

        Set<Long> memberIds = new HashSet<>();
        members.findBySubscription(session.getSubscription()).forEach(member -> memberIds.add(member.getPerson().getId()));
        existing.values().stream().filter(entry -> !entry.isGuest()).forEach(entry -> memberIds.add(entry.getPerson().getId()));
        Set<Long> desired = new HashSet<>();
        for (Long personId : request.memberIds()) {
            SessionAttendance previous = existing.get(personId);
            if (personId == null || !memberIds.contains(personId) || (previous != null && previous.isGuest()))
                throw new DomainException("Этот человек не является участником абонемента для данной тренировки.");
            if (!desired.add(personId)) throw new DomainException("Один человек не может быть отмечен дважды.");
            if (previous == null) attendance.save(new SessionAttendance(session, get(people, personId, "Person"), false));
        }

        for (AttendanceGuestRequest guest : request.guests()) {
            if (guest == null) throw new DomainException("Укажите данные гостя.");
            Person person;
            if (guest.personId() != null) {
                if (guest.fullName() != null || guest.phone() != null)
                    throw new DomainException("Для гостя из справочника передайте только personId и сумму.");
                person = get(people, guest.personId(), "Person");
            } else {
                if (guest.fullName() == null || guest.fullName().isBlank() || guest.fullName().length() > 255
                        || (guest.phone() != null && guest.phone().length() > 255))
                    throw new DomainException("Укажите имя нового гостя (не более 255 символов).");
                person = people.save(new Person(guest.fullName().trim(), guest.phone(), null));
            }
            Long personId = person.getId();
            SessionAttendance previous = existing.get(personId);
            if (memberIds.contains(personId) && (previous == null || !previous.isGuest()))
                throw new DomainException("Участника абонемента нужно отметить в основном списке.");
            if (!desired.add(personId)) throw new DomainException("Один человек не может быть отмечен дважды.");
            long amount = guest.amount() == null ? currentRate().orElseThrow(() ->
                    new DomainException("Нет действующего тарифа разовой тренировки. Укажите сумму для гостя.")) : guest.amount();
            if (amount < 0) throw new DomainException("Сумма разового посещения не может быть отрицательной.");
            SessionAttendance entry = previous == null ? new SessionAttendance(session, person, true) : previous;
            Charge charge = entry.getCharge();
            long allocated = charge == null ? 0 : charges.allocatedFor(charge);
            if (amount < allocated) throw new DomainException("Сумма гостя не может быть меньше распределённой оплаты: " + allocated + " ₸.");
            if (amount == 0 && charge != null) {
                entry.setCharge(null);
                attendance.saveAndFlush(entry);
                charges.delete(charge);
            } else if (amount > 0) {
                if (charge == null) entry.setCharge(charges.save(new Charge(person, session.getSubscription(), ChargeType.MANUAL,
                        amount, session.getStartsAt().toLocalDate(), "Разовая тренировка " + session.getStartsAt().toString().replace('T', ' '))));
                else charge.edit(amount, charge.getChargedAt(), charge.getDescription());
            }
            attendance.save(entry);
        }

        for (SessionAttendance entry : existing.values()) {
            if (desired.contains(entry.getPerson().getId())) continue;
            Charge charge = entry.getCharge();
            if (charge != null && charges.allocatedFor(charge) > 0)
                throw new DomainException("Нельзя убрать гостя с распределённой оплатой. Сначала отмените распределение в окне редактирования оплаты.");
            attendance.delete(entry);
            attendance.flush();
            if (charge != null) charges.delete(charge);
        }
        session.recordAttendance();
        sessions.flush();
        return view(session);
    }

    private Optional<Long> currentRate() {
        return rates.applicable(RateCode.DROP_IN, LocalDate.now(clock)).stream().findFirst().map(PricingRate::getAmount);
    }

    private AttendanceView view(TrainingSession session) {
        Map<Long, SessionAttendance> present = new HashMap<>();
        attendance.findBySession(session).forEach(entry -> present.put(entry.getPerson().getId(), entry));
        Map<Long, AttendancePersonView> roster = new HashMap<>();
        for (SubscriptionMember member : members.findBySubscription(session.getSubscription())) {
            Person person = member.getPerson();
            roster.put(person.getId(), new AttendancePersonView(person.getId(), person.getFullName(), false, false, null, null, 0));
        }
        for (SessionAttendance entry : present.values()) {
            Charge charge = entry.getCharge();
            roster.put(entry.getPerson().getId(), new AttendancePersonView(entry.getPerson().getId(), entry.getPerson().getFullName(),
                    entry.isGuest(), true, entry.isGuest() ? (charge == null ? 0L : charge.getAmount()) : null,
                    charge == null ? null : charge.getId(), charge == null ? 0 : charges.allocatedFor(charge)));
        }
        return new AttendanceView(session.getId(), session.getStartsAt(), session.getAttendanceRevision(), available(session),
                LocalDate.now(clock), currentRate().orElse(null), roster.values().stream()
                .sorted(Comparator.comparing(AttendancePersonView::guest).thenComparing(AttendancePersonView::personName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(AttendancePersonView::personId)).toList());
    }
}
