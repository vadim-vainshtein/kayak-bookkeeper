package kz.kayak.bookkeeper.service;

import jakarta.transaction.Transactional;
import kz.kayak.bookkeeper.api.Api;
import kz.kayak.bookkeeper.domain.*;
import kz.kayak.bookkeeper.repository.*;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BookkeepingService {
    private final PersonRepository people; private final PricingRateRepository rates;
    private final SubscriptionRepository subscriptions; private final TrainingSessionRepository sessions; private final SubscriptionMemberRepository members;
    private final AttendanceRepository attendance; private final ChargeRepository charges; private final PaymentRepository payments; private final PaymentAllocationRepository allocations;
    public BookkeepingService(PersonRepository people, PricingRateRepository rates, SubscriptionRepository subscriptions, TrainingSessionRepository sessions, SubscriptionMemberRepository members, AttendanceRepository attendance, ChargeRepository charges, PaymentRepository payments, PaymentAllocationRepository allocations) {
        this.people=people; this.rates=rates; this.subscriptions=subscriptions; this.sessions=sessions; this.members=members; this.attendance=attendance; this.charges=charges; this.payments=payments; this.allocations=allocations;
    }
    public Api.PersonView createPerson(Api.PersonRequest r) { return personView(people.save(new Person(r.fullName(), r.phone(), r.note()))); }
    public List<Api.PersonView> listPeople() { return people.findAll().stream().map(this::personView).toList(); }
    @Transactional public Api.PersonView updatePerson(long id, Api.PersonUpdateRequest r) { Person person=get(people,id,"Person"); person.update(r.fullName(),r.phone(),r.note(),r.active()); return personView(person); }
    public Api.RateView createRate(Api.RateRequest r) {
        if (r.effectiveTo()!=null && r.effectiveTo().isBefore(r.effectiveFrom())) throw new DomainException("effectiveTo cannot be before effectiveFrom");
        for (PricingRate existing: rates.findByCode(r.code())) if (overlaps(r.effectiveFrom(),r.effectiveTo(),existing.getEffectiveFrom(),existing.getEffectiveTo())) throw new DomainException("Pricing-rate periods for one code must not overlap");
        return rateView(rates.save(new PricingRate(r.code(),r.amount(),r.effectiveFrom(),r.effectiveTo())));
    }
    public List<Api.RateView> listRates() { return rates.findAll().stream().map(this::rateView).toList(); }
    @Transactional public void deleteRate(long id) { rates.delete(get(rates,id,"Pricing rate")); }
    @Transactional public Api.RateView endRate(long id, Api.RateEndRequest r) {
        PricingRate rate=get(rates,id,"Pricing rate");
        if (r.effectiveTo().isBefore(rate.getEffectiveFrom())) throw new DomainException("effectiveTo cannot be before effectiveFrom");
        for(PricingRate other:rates.findByCode(rate.getCode())) if(!other.getId().equals(rate.getId()) && overlaps(rate.getEffectiveFrom(),r.effectiveTo(),other.getEffectiveFrom(),other.getEffectiveTo())) throw new DomainException("Pricing-rate periods for one code must not overlap");
        rate.setEffectiveTo(r.effectiveTo()); return rateView(rate);
    }
    @Transactional public Api.SubscriptionView createExplicitSubscription(Api.ExplicitSubscriptionRequest r) {
        if (new HashSet<>(r.startsAt()).size()!=r.startsAt().size()) throw new DomainException("Training dates must be distinct");
        return createSubscription(r.startsAt().stream().sorted().toList(),r.capacity(),r.title(),r.description());
    }
    @Transactional public Api.SubscriptionView createRecurringSubscription(Api.RecurringSubscriptionRequest r) {
        LocalDate first=r.startDate().with(TemporalAdjusters.nextOrSame(r.dayOfWeek())); List<LocalDateTime> dates=new ArrayList<>();
        for(int i=0;i<r.sessionCount();i++) dates.add(LocalDateTime.of(first.plusWeeks(i),r.startTime())); return createSubscription(dates,r.capacity(),r.title(),r.description());
    }
    private Api.SubscriptionView createSubscription(List<LocalDateTime> dates,Integer capacity,String title,String description) { Subscription s=subscriptions.save(new Subscription(capacity,title,description)); dates.forEach(d->sessions.save(new TrainingSession(s,d))); return subscriptionView(s); }
    @Transactional public Api.SubscriptionView addMember(long id,Api.MemberRequest r) {
        Subscription s=lockedSubscription(id); ensureDraft(s); Person p=get(people,r.personId(),"Person");
        if(!p.isActive()) throw new DomainException("Inactive person cannot join a subscription"); if(members.countBySubscription(s)>=s.getCapacity()) throw new DomainException("Subscription capacity has been reached"); if(members.findBySubscriptionAndPerson(s,p).isPresent()) throw new DomainException("Person is already in this subscription");
        members.save(new SubscriptionMember(s,p,r.usesSkirt(),r.usesPaddle(),r.usesKayak())); return subscriptionView(s);
    }
    @Transactional public Api.SubscriptionView finalizeSubscription(long id) {
        Subscription s=lockedSubscription(id); ensureDraft(s); List<SubscriptionMember> group=members.findBySubscription(s); List<TrainingSession> ss=sessionsFor(s);
        if(group.isEmpty()) throw new DomainException("Cannot finalize a subscription without members"); if(ss.isEmpty()) throw new DomainException("Cannot finalize a subscription without sessions");
        long poolRentalTotal=ss.stream().mapToLong(x->applicableAmount(RateCode.POOL_RENTAL,x.getStartsAt().toLocalDate())).sum();
        long poolShare=ss.stream().mapToLong(x->ceilDiv(applicableAmount(RateCode.POOL_RENTAL,x.getStartsAt().toLocalDate()),group.size())).sum(); s.finalizeWith(poolRentalTotal); LocalDate chargedAt=ss.getFirst().getStartsAt().toLocalDate();
        for(SubscriptionMember member:group) {
            long skirt=member.isUsesSkirt()?sumRate(ss,RateCode.SKIRT_RENTAL):0, paddle=member.isUsesPaddle()?sumRate(ss,RateCode.PADDLE_RENTAL):0, kayak=member.isUsesKayak()?sumRate(ss,RateCode.KAYAK_RENTAL):0;
            member.snapshotFees(poolShare,skirt,paddle,kayak); charges.save(new Charge(member.getPerson(),s,null,ChargeType.POOL_SHARE,poolShare,chargedAt,"Доля аренды бассейна за абонемент"));
            if(skirt>0) charges.save(new Charge(member.getPerson(),s,null,ChargeType.SKIRT_RENT,skirt,chargedAt,"Аренда юбки за абонемент"));
            if(paddle>0) charges.save(new Charge(member.getPerson(),s,null,ChargeType.PADDLE_RENT,paddle,chargedAt,"Аренда весла за абонемент"));
            if(kayak>0) charges.save(new Charge(member.getPerson(),s,null,ChargeType.KAYAK_RENT,kayak,chargedAt,"Аренда каяка за абонемент"));
        } return subscriptionView(s);
    }
    @Transactional public Api.SubscriptionView completeSubscription(long id) { Subscription s=lockedSubscription(id); if(s.getStatus()!=SubscriptionStatus.FINALIZED) throw new DomainException("Only a finalized subscription can be completed"); s.complete(); return subscriptionView(s); }
    public List<Api.SubscriptionView> listSubscriptions() {
        List<Subscription> all=subscriptions.findAll().stream().sorted(Comparator.comparing(Subscription::getId)).toList();
        if(all.isEmpty()) return List.of();
        Map<Long,List<SubscriptionMember>> membersBySubscription=members.findBySubscriptionInWithSubscriptionAndPerson(all).stream().collect(Collectors.groupingBy(member->member.getSubscription().getId()));
        Map<Long,List<TrainingSession>> sessionsBySubscription=sessions.findBySubscriptionInWithSubscription(all).stream().collect(Collectors.groupingBy(session->session.getSubscription().getId()));
        return all.stream().map(subscription->subscriptionView(subscription,membersBySubscription.getOrDefault(subscription.getId(),List.of()),sessionsBySubscription.getOrDefault(subscription.getId(),List.of()))).toList();
    }
    public Api.SubscriptionView getSubscription(long id) { return subscriptionView(get(subscriptions,id,"Subscription")); }
    @Transactional public Api.AttendanceView markSubscriptionAttendance(long id,Api.AttendanceRequest r) {
        TrainingSession session=get(sessions,id,"Training session"); Subscription s=lockedSubscription(session.getSubscription().getId()); if(s.getStatus()!=SubscriptionStatus.FINALIZED) throw new DomainException("Finalize subscription before taking attendance"); if(r.status()==AttendanceStatus.PLANNED) throw new DomainException("Attendance must be PRESENT or ABSENT");
        Person p=get(people,r.personId(),"Person"); SubscriptionMember member=members.findBySubscriptionAndPerson(s,p).orElseThrow(()->new DomainException("Person is not a member of this session's subscription")); Attendance entry=attendance.findByTrainingSessionAndPerson(session,p).orElseGet(()->new Attendance(session,p,member,AttendanceType.SUBSCRIPTION,r.status()));
        if(entry.getStatus()==AttendanceStatus.ABSENT&&r.status()==AttendanceStatus.PRESENT&&dropInsConsumeAllAbsences(session)) throw new DomainException("Cannot mark participant present: all released places are occupied by drop-in visitors"); entry.setStatus(r.status()); return attendanceView(attendance.save(entry));
    }
    @Transactional public Api.AttendanceView addDropIn(long id,Api.DropInRequest r) {
        TrainingSession session=get(sessions,id,"Training session"); Subscription s=lockedSubscription(session.getSubscription().getId()); if(s.getStatus()!=SubscriptionStatus.FINALIZED) throw new DomainException("Finalize subscription before admitting drop-in visitors"); Person p=get(people,r.personId(),"Person"); if(attendance.findByTrainingSessionAndPerson(session,p).isPresent()) throw new DomainException("Person already has attendance for this session"); if(dropInsConsumeAllAbsences(session)) throw new DomainException("No marked absence has released a place for a drop-in visitor");
        Attendance entry=attendance.save(new Attendance(session,p,null,AttendanceType.DROP_IN,AttendanceStatus.PRESENT)); LocalDate date=session.getStartsAt().toLocalDate(); charges.save(new Charge(p,s,entry,ChargeType.DROP_IN,applicableAmount(RateCode.DROP_IN,date),date,"Разовая тренировка")); return attendanceView(entry);
    }
    public Api.RosterView roster(long id) {
        TrainingSession session=get(sessions,id,"Training session"); List<Attendance> recorded=attendance.findByTrainingSessionOrderByTypeAscPersonFullNameAsc(session); Map<Long,Attendance> marked=recorded.stream().filter(a->a.getType()==AttendanceType.SUBSCRIPTION).collect(Collectors.toMap(a->a.getPerson().getId(),a->a)); List<Api.AttendanceView> entries=new ArrayList<>();
        for(SubscriptionMember m:members.findBySubscription(session.getSubscription())) { Attendance a=marked.get(m.getPerson().getId()); entries.add(a==null?new Api.AttendanceView(null,m.getPerson().getId(),m.getPerson().getFullName(),AttendanceType.SUBSCRIPTION,AttendanceStatus.PLANNED):attendanceView(a)); } recorded.stream().filter(a->a.getType()==AttendanceType.DROP_IN).map(this::attendanceView).forEach(entries::add); entries.sort(Comparator.comparing(Api.AttendanceView::type).thenComparing(Api.AttendanceView::personName)); return new Api.RosterView(id,session.getSubscription().getCapacity(),Math.max(0,absences(session)-visitors(session)),entries);
    }
    @Transactional public Api.ChargeView createManualCharge(Api.ManualChargeRequest r) { Person p=get(people,r.personId(),"Person"); Subscription s=r.subscriptionId()==null?null:get(subscriptions,r.subscriptionId(),"Subscription"); if(s!=null && members.findBySubscriptionAndPerson(s,p).isEmpty()) throw new DomainException("Person is not a member of this subscription"); return chargeView(charges.save(new Charge(p,s,null,ChargeType.MANUAL,r.amount(),r.chargedAt(),r.description()))); }
    @Transactional public Api.ChargeView editCharge(long id,Api.ChargeEditRequest r) { Charge c=get(charges,id,"Charge"); if(charges.allocatedFor(c)>r.amount()) throw new DomainException("Charge amount cannot be below allocated payment amount"); c.edit(r.amount(),r.chargedAt(),r.description()); syncEditedAutomaticCharge(c); return chargeView(c); }
    public Api.PaymentView createPayment(Api.PaymentRequest r) { Person p=get(people,r.personId(),"Person"); return paymentView(payments.save(new Payment(p,r.amount(),r.paidAt(),r.method(),r.note()))); }
    @Transactional public void allocatePayment(long id,Api.AllocationRequest r) { Payment p=get(payments,id,"Payment"); Charge c=get(charges,r.chargeId(),"Charge"); if(!p.getPerson().getId().equals(c.getPerson().getId())) throw new DomainException("Payment and charge must belong to the same person"); if(payments.allocatedFor(p)+r.amount()>p.getAmount()) throw new DomainException("Allocation exceeds payment amount"); if(charges.allocatedFor(c)+r.amount()>c.getAmount()) throw new DomainException("Allocation exceeds charge amount"); allocations.save(new PaymentAllocation(p,c,r.amount())); }
    public Api.BalanceView balance(long id) { Person p=get(people,id,"Person"); List<Charge> c=charges.findByPerson(p); long total=c.stream().mapToLong(Charge::getAmount).sum(), paid=payments.findByPerson(p).stream().mapToLong(Payment::getAmount).sum(); return new Api.BalanceView(id,total,paid,total-paid,c.stream().map(this::chargeView).toList()); }
    private long sumRate(List<TrainingSession> ss,RateCode c){return ss.stream().mapToLong(s->applicableAmount(c,s.getStartsAt().toLocalDate())).sum();} private boolean dropInsConsumeAllAbsences(TrainingSession s){return visitors(s)>=absences(s);} private long absences(TrainingSession s){return attendance.countByTrainingSessionAndTypeAndStatus(s,AttendanceType.SUBSCRIPTION,AttendanceStatus.ABSENT);} private long visitors(TrainingSession s){return attendance.countByTrainingSessionAndTypeAndStatus(s,AttendanceType.DROP_IN,AttendanceStatus.PRESENT);} private List<TrainingSession> sessionsFor(Subscription s){return sessions.findAll().stream().filter(x->x.getSubscription().getId().equals(s.getId())).sorted(Comparator.comparing(TrainingSession::getStartsAt)).toList();} private void syncEditedAutomaticCharge(Charge c){if(c.getSubscription()==null||c.getType()==ChargeType.MANUAL||c.getType()==ChargeType.DROP_IN)return; SubscriptionMember member=members.findBySubscriptionAndPerson(c.getSubscription(),c.getPerson()).orElseThrow(()->new DomainException("Automatic charge has no subscription member")); member.editAutomaticFee(c.getType(),c.getAmount());} private Subscription lockedSubscription(long id){return subscriptions.findWithLockById(id).orElseThrow(()->new NoSuchElementException("Subscription "+id+" was not found"));} private long applicableAmount(RateCode c,LocalDate d){return rates.applicable(c,d).stream().findFirst().orElseThrow(()->new DomainException("No active "+c+" rate on "+d)).getAmount();} private static boolean overlaps(LocalDate af,LocalDate at,LocalDate bf,LocalDate bt){return(at==null||!at.isBefore(bf))&&(bt==null||!bt.isBefore(af));} private static long ceilDiv(long v,long d){return(v+d-1)/d;} private void ensureDraft(Subscription s){if(s.getStatus()!=SubscriptionStatus.DRAFT)throw new DomainException("Only a draft subscription can be changed");} private <T>T get(org.springframework.data.jpa.repository.JpaRepository<T,Long> r,long id,String label){return r.findById(id).orElseThrow(()->new NoSuchElementException(label+" "+id+" was not found"));}
    private Api.PersonView personView(Person p){return new Api.PersonView(p.getId(),p.getFullName(),p.getPhone(),p.getNote(),p.isActive());} private Api.RateView rateView(PricingRate r){return new Api.RateView(r.getId(),r.getCode(),r.getAmount(),r.getEffectiveFrom(),r.getEffectiveTo());} private Api.MemberView memberView(SubscriptionMember m){return new Api.MemberView(m.getId(),m.getPerson().getId(),m.getPerson().getFullName(),m.isUsesSkirt(),m.isUsesPaddle(),m.isUsesKayak(),m.getPoolShare(),m.getSkirtFee(),m.getPaddleFee(),m.getKayakFee());} private Api.SessionView sessionView(TrainingSession s){return new Api.SessionView(s.getId(),s.getStartsAt());} private Api.SubscriptionView subscriptionView(Subscription s){return subscriptionView(s,members.findBySubscription(s),sessionsFor(s));} private Api.SubscriptionView subscriptionView(Subscription s,List<SubscriptionMember> subscriptionMembers,List<TrainingSession> subscriptionSessions){return new Api.SubscriptionView(s.getId(),s.getCapacity(),s.getTitle(),s.getDescription(),s.getStatus(),s.getPoolRentalAmount(),subscriptionMembers.stream().map(this::memberView).toList(),subscriptionSessions.stream().sorted(Comparator.comparing(TrainingSession::getStartsAt)).map(this::sessionView).toList());} private Api.AttendanceView attendanceView(Attendance a){return new Api.AttendanceView(a.getId(),a.getPerson().getId(),a.getPerson().getFullName(),a.getType(),a.getStatus());} private Api.PaymentView paymentView(Payment p){return new Api.PaymentView(p.getId(),p.getPerson().getId(),p.getAmount(),p.getPaidAt(),p.getMethod(),p.getNote());} private Api.ChargeView chargeView(Charge c){return new Api.ChargeView(c.getId(),c.getType(),c.getAmount(),charges.allocatedFor(c),c.getChargedAt(),c.getDescription(),c.getSubscription()==null?null:c.getSubscription().getId(),c.getAttendance()==null?null:c.getAttendance().getId());}
}
