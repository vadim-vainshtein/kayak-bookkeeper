package kz.kayak.bookkeeper.service;

import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kz.kayak.bookkeeper.api.dto.AttendanceRequest;
import kz.kayak.bookkeeper.api.dto.AttendanceView;
import kz.kayak.bookkeeper.api.dto.DropInRequest;
import kz.kayak.bookkeeper.api.dto.RosterView;
import kz.kayak.bookkeeper.domain.Attendance;
import kz.kayak.bookkeeper.domain.AttendanceStatus;
import kz.kayak.bookkeeper.domain.AttendanceType;
import kz.kayak.bookkeeper.domain.Charge;
import kz.kayak.bookkeeper.domain.ChargeType;
import kz.kayak.bookkeeper.domain.Person;
import kz.kayak.bookkeeper.domain.RateCode;
import kz.kayak.bookkeeper.domain.Subscription;
import kz.kayak.bookkeeper.domain.SubscriptionMember;
import kz.kayak.bookkeeper.domain.SubscriptionStatus;
import kz.kayak.bookkeeper.domain.TrainingSession;
import kz.kayak.bookkeeper.repository.AttendanceRepository;
import kz.kayak.bookkeeper.repository.ChargeRepository;
import kz.kayak.bookkeeper.repository.PersonRepository;
import kz.kayak.bookkeeper.repository.SubscriptionMemberRepository;
import kz.kayak.bookkeeper.repository.TrainingSessionRepository;
import org.springframework.stereotype.Service;

import static kz.kayak.bookkeeper.service.EntityLookup.get;
import static kz.kayak.bookkeeper.service.EntityViews.attendanceView;

/** Maintains attendance and admits drop-ins only into released places. */
@Service
public class AttendanceService {
    private final TrainingSessionRepository sessions;
    private final PersonRepository people;
    private final SubscriptionMemberRepository members;
    private final AttendanceRepository attendance;
    private final ChargeRepository charges;
    private final SubscriptionAccess access;
    private final PricingRateService pricing;

    public AttendanceService(
            TrainingSessionRepository sessions,
            PersonRepository people,
            SubscriptionMemberRepository members,
            AttendanceRepository attendance,
            ChargeRepository charges,
            SubscriptionAccess access,
            PricingRateService pricing) {
        this.sessions = sessions;
        this.people = people;
        this.members = members;
        this.attendance = attendance;
        this.charges = charges;
        this.access = access;
        this.pricing = pricing;
    }

    @Transactional
    public AttendanceView markSubscriptionAttendance(long id, AttendanceRequest r) {
        TrainingSession session = get(sessions, id, "Training session");
        Subscription s = access.lockedSubscription(session.getSubscription().getId());
        if (s.getStatus() != SubscriptionStatus.FINALIZED)
            throw new DomainException("Finalize subscription before taking attendance");
        if (r.status() == AttendanceStatus.PLANNED) throw new DomainException("Attendance must be PRESENT or ABSENT");
        Person p = get(people, r.personId(), "Person");
        SubscriptionMember member = members.findBySubscriptionAndPerson(s, p).orElseThrow(() -> new DomainException("Person is not a member of this session's subscription"));
        Attendance entry = attendance.findByTrainingSessionAndPerson(session, p).orElseGet(() -> new Attendance(session, p, member, AttendanceType.SUBSCRIPTION, r.status()));
        if (entry.getStatus() == AttendanceStatus.ABSENT && r.status() == AttendanceStatus.PRESENT && dropInsConsumeAllAbsences(session))
            throw new DomainException("Cannot mark participant present: all released places are occupied by drop-in visitors");
        entry.setStatus(r.status());
        return attendanceView(attendance.save(entry));
    }

    @Transactional
    public AttendanceView addDropIn(long id, DropInRequest r) {
        TrainingSession session = get(sessions, id, "Training session");
        Subscription s = access.lockedSubscription(session.getSubscription().getId());
        if (s.getStatus() != SubscriptionStatus.FINALIZED)
            throw new DomainException("Finalize subscription before admitting drop-in visitors");
        Person p = get(people, r.personId(), "Person");
        if (attendance.findByTrainingSessionAndPerson(session, p).isPresent())
            throw new DomainException("Person already has attendance for this session");
        if (dropInsConsumeAllAbsences(session))
            throw new DomainException("No marked absence has released a place for a drop-in visitor");
        Attendance entry = attendance.save(new Attendance(session, p, null, AttendanceType.DROP_IN, AttendanceStatus.PRESENT));
        LocalDate date = session.getStartsAt().toLocalDate();
        long amount = pricing.applicableAmount(RateCode.DROP_IN, date);
        if (amount > 0) charges.save(new Charge(p, s, entry, ChargeType.DROP_IN, amount, date, "Разовая тренировка"));
        return attendanceView(entry);
    }

    public RosterView roster(long id) {
        TrainingSession session = get(sessions, id, "Training session");
        List<Attendance> recorded = attendance.findByTrainingSessionOrderByTypeAscPersonFullNameAsc(session);
        Map<Long, Attendance> marked = recorded.stream().filter(a -> a.getType() == AttendanceType.SUBSCRIPTION).collect(Collectors.toMap(a -> a.getPerson().getId(), a -> a));
        List<AttendanceView> entries = new ArrayList<>();
        for (SubscriptionMember m : members.findBySubscription(session.getSubscription())) {
            Attendance a = marked.get(m.getPerson().getId());
            entries.add(a == null ? new AttendanceView(null, m.getPerson().getId(), m.getPerson().getFullName(), AttendanceType.SUBSCRIPTION, AttendanceStatus.PLANNED) : attendanceView(a));
        }
        recorded.stream().filter(a -> a.getType() == AttendanceType.DROP_IN).map(EntityViews::attendanceView).forEach(entries::add);
        entries.sort(Comparator.comparing(AttendanceView::type).thenComparing(AttendanceView::personName, String.CASE_INSENSITIVE_ORDER).thenComparing(AttendanceView::personId));
        return new RosterView(id, session.getSubscription().getCapacity(), Math.max(0, absences(session) - visitors(session)), entries);
    }

    private boolean dropInsConsumeAllAbsences(TrainingSession s) {
        return visitors(s) >= absences(s);
    }

    private long absences(TrainingSession s) {
        return attendance.countByTrainingSessionAndTypeAndStatus(s, AttendanceType.SUBSCRIPTION, AttendanceStatus.ABSENT);
    }

    private long visitors(TrainingSession s) {
        return attendance.countByTrainingSessionAndTypeAndStatus(s, AttendanceType.DROP_IN, AttendanceStatus.PRESENT);
    }
}
