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
    public List<Api.PersonBalanceSummaryView> listPersonBalances() {
        Map<Long, Long> chargedByPerson = charges.findAll().stream().collect(Collectors.groupingBy(charge -> charge.getPerson().getId(), Collectors.summingLong(Charge::getAmount)));
        Map<Long, Long> paidByPerson = payments.findAll().stream().collect(Collectors.groupingBy(payment -> payment.getPerson().getId(), Collectors.summingLong(Payment::getAmount)));
        return people.findAll().stream().map(person -> {
            long charged = chargedByPerson.getOrDefault(person.getId(), 0L);
            long paid = paidByPerson.getOrDefault(person.getId(), 0L);
            return new Api.PersonBalanceSummaryView(person.getId(), person.getFullName(), person.isActive(), charged, paid, charged - paid);
        }).toList();
    }
    @Transactional public Api.FinancialReportView financialReport(LocalDate from, LocalDate to) { return financialReport(from, to, null); }
    @Transactional public Api.FinancialReportView financialReport(LocalDate from, LocalDate to, Long subscriptionId) {
        if (to.isBefore(from)) throw new DomainException("Report end date cannot be before start date");
        Subscription filteredSubscription=subscriptionId==null ? null : get(subscriptions, subscriptionId, "Subscription");
        List<Person> allPeople=people.findAll(); List<Charge> allCharges=charges.findAll(); List<Payment> allPayments=payments.findAll(); List<PaymentAllocation> allAllocations=allocations.findAll();
        List<Charge> reportCharges=filteredSubscription==null ? allCharges : allCharges.stream().filter(charge -> charge.getSubscription()!=null && subscriptionId.equals(charge.getSubscription().getId())).toList();
        List<PaymentAllocation> reportAllocations=filteredSubscription==null ? allAllocations : allAllocations.stream().filter(allocation -> allocation.getCharge().getSubscription()!=null && subscriptionId.equals(allocation.getCharge().getSubscription().getId())).toList();
        List<Subscription> reportSubscriptions=filteredSubscription==null ? subscriptions.findAll() : List.of(filteredSubscription);
        Map<Long, List<SubscriptionMember>> membersBySubscription=reportSubscriptions.isEmpty() ? Map.of() : members.findBySubscriptionInWithSubscriptionAndPerson(reportSubscriptions).stream().collect(Collectors.groupingBy(member -> member.getSubscription().getId()));
        Set<Long> filteredPersonIds=filteredSubscription==null ? Set.of() : new HashSet<>();
        if (filteredSubscription!=null) { reportCharges.forEach(charge -> filteredPersonIds.add(charge.getPerson().getId())); membersBySubscription.getOrDefault(filteredSubscription.getId(), List.of()).forEach(member -> filteredPersonIds.add(member.getPerson().getId())); }
        List<Person> reportPeople=filteredSubscription==null ? allPeople : allPeople.stream().filter(person -> filteredPersonIds.contains(person.getId())).toList();
        Map<Long, List<Charge>> chargesByPerson=reportCharges.stream().collect(Collectors.groupingBy(charge -> charge.getPerson().getId()));
        Map<Long, List<Payment>> paymentsByPerson=allPayments.stream().collect(Collectors.groupingBy(payment -> payment.getPerson().getId()));
        Map<Long, List<PaymentAllocation>> allocationsByPerson=reportAllocations.stream().collect(Collectors.groupingBy(allocation -> allocation.getCharge().getPerson().getId()));
        Map<Long, Long> allocatedByPaymentAtEnd=allAllocations.stream().filter(allocation -> !allocation.getAllocatedAt().isAfter(to)).collect(Collectors.groupingBy(allocation -> allocation.getPayment().getId(), Collectors.summingLong(PaymentAllocation::getAmount)));
        long chargedInPeriod=reportCharges.stream().filter(charge -> inPeriod(charge.getChargedAt(), from, to)).mapToLong(Charge::getAmount).sum();
        long receivedInPeriod=filteredSubscription==null ? allPayments.stream().filter(payment -> inPeriod(payment.getPaidAt(), from, to)).mapToLong(Payment::getAmount).sum() : reportAllocations.stream().filter(allocation -> inPeriod(allocation.getAllocatedAt(), from, to)).mapToLong(PaymentAllocation::getAmount).sum();
        long unallocatedAtEnd=filteredSubscription==null ? allPayments.stream().filter(payment -> !payment.getPaidAt().isAfter(to)).mapToLong(payment -> payment.getAmount()-allocatedByPaymentAtEnd.getOrDefault(payment.getId(), 0L)).sum() : 0;
        List<Api.PersonFinancialReportView> personRows=reportPeople.stream().map(person -> filteredSubscription==null ? personReport(person, chargesByPerson.getOrDefault(person.getId(), List.of()), paymentsByPerson.getOrDefault(person.getId(), List.of()), from, to) : subscriptionPersonReport(person, chargesByPerson.getOrDefault(person.getId(), List.of()), allocationsByPerson.getOrDefault(person.getId(), List.of()), from, to)).sorted(Comparator.comparingLong(Api.PersonFinancialReportView::balanceAtEnd).reversed().thenComparing(Api.PersonFinancialReportView::fullName)).toList();
        long debtAtEnd=personRows.stream().mapToLong(row -> Math.max(0, row.balanceAtEnd())).sum();
        long creditAtEnd=personRows.stream().mapToLong(row -> Math.max(0, -row.balanceAtEnd())).sum();
        Map<Long, Long> allocatedInPeriodBySubscription=new HashMap<>(), allocatedThroughEndBySubscription=new HashMap<>();
        for (PaymentAllocation allocation : reportAllocations) {
            Charge charge=allocation.getCharge(); Payment payment=allocation.getPayment();
            if (charge.getSubscription()==null) continue;
            long allocatedSubscriptionId=charge.getSubscription().getId();
            if (inPeriod(allocation.getAllocatedAt(), from, to)) allocatedInPeriodBySubscription.merge(allocatedSubscriptionId, allocation.getAmount(), Long::sum);
            if (!allocation.getAllocatedAt().isAfter(to) && !payment.getPaidAt().isAfter(to) && !charge.getChargedAt().isAfter(to)) allocatedThroughEndBySubscription.merge(allocatedSubscriptionId, allocation.getAmount(), Long::sum);
        }
        Map<Long, List<Charge>> chargesBySubscription=reportCharges.stream().filter(charge -> charge.getSubscription()!=null).collect(Collectors.groupingBy(charge -> charge.getSubscription().getId()));
        Map<Long, List<TrainingSession>> sessionsBySubscription=reportSubscriptions.isEmpty() ? Map.of() : sessions.findBySubscriptionInWithSubscription(reportSubscriptions).stream().collect(Collectors.groupingBy(session -> session.getSubscription().getId()));
        List<Api.SubscriptionFinancialReportView> subscriptionRows=reportSubscriptions.stream().map(subscription -> subscriptionReport(subscription, chargesBySubscription.getOrDefault(subscription.getId(), List.of()), allocatedInPeriodBySubscription.getOrDefault(subscription.getId(), 0L), allocatedThroughEndBySubscription.getOrDefault(subscription.getId(), 0L), membersBySubscription.getOrDefault(subscription.getId(), List.of()), sessionsBySubscription.getOrDefault(subscription.getId(), List.of()), from, to)).filter(row -> filteredSubscription!=null || row.chargedInPeriod()!=0 || row.outstandingAtEnd()!=0 || overlapsPeriod(row.firstSessionDate(), row.lastSessionDate(), from, to)).sorted(Comparator.comparing(Api.SubscriptionFinancialReportView::firstSessionDate, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(Api.SubscriptionFinancialReportView::subscriptionId)).toList();
        long allocatedFundsAtEnd=allocatedThroughEndBySubscription.values().stream().mapToLong(Long::longValue).sum();
        long poolRentalCostAtEnd=reportSubscriptions.stream().mapToLong(subscription -> poolRentalCostAtEnd(subscription, sessionsBySubscription.getOrDefault(subscription.getId(), List.of()), to)).sum();
        return new Api.FinancialReportView(from, to, new Api.FinancialReportSummaryView(chargedInPeriod, receivedInPeriod, debtAtEnd, creditAtEnd, unallocatedAtEnd, allocatedFundsAtEnd, poolRentalCostAtEnd, allocatedFundsAtEnd-poolRentalCostAtEnd), subscriptionRows, personRows);
    }
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
    @Transactional public Api.SubscriptionView createRecurringSubscriptionWithMembers(Api.RecurringSubscriptionWithMembersRequest r) {
        if(r.members().isEmpty()) throw new DomainException("Subscription must have at least one member");
        int capacity=r.capacity()==null ? 8 : r.capacity();
        if(r.members().size()>capacity) throw new DomainException("Subscription capacity has been reached");
        Set<Long> personIds=new HashSet<>(); List<Person> selectedPeople=new ArrayList<>();
        for(Api.MemberRequest member:r.members()) {
            if(!personIds.add(member.personId())) throw new DomainException("Person is already in this subscription");
            Person person=get(people,member.personId(),"Person");
            if(!person.isActive()) throw new DomainException("Inactive person cannot join a subscription");
            selectedPeople.add(person);
        }
        LocalDate first=r.startDate().with(TemporalAdjusters.nextOrSame(r.dayOfWeek())); List<LocalDateTime> dates=new ArrayList<>();
        for(int i=0;i<r.sessionCount();i++) dates.add(LocalDateTime.of(first.plusWeeks(i),r.startTime()));
        Subscription subscription=subscriptions.save(new Subscription(r.capacity(),r.title(),r.description()));
        dates.forEach(date->sessions.save(new TrainingSession(subscription,date)));
        for(int i=0;i<r.members().size();i++) { Api.MemberRequest member=r.members().get(i); members.save(new SubscriptionMember(subscription,selectedPeople.get(i),member.usesSkirt(),member.usesPaddle(),member.usesKayak())); }
        return subscriptionView(subscription);
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
            member.snapshotFees(poolShare,skirt,paddle,kayak); if(poolShare>0) charges.save(new Charge(member.getPerson(),s,null,ChargeType.POOL_SHARE,poolShare,chargedAt,"Доля аренды бассейна за абонемент"));
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
    @Transactional public Api.SubscriptionView updateSubscriptionMetadata(long id, Api.SubscriptionMetadataUpdateRequest r) { Subscription subscription=lockedSubscription(id); subscription.updateMetadata(r.title(),r.description()); return subscriptionView(subscription); }
    @Transactional public Api.SubscriptionView updateDraftSubscription(long id, Api.DraftSubscriptionUpdateRequest r) {
        Subscription subscription=lockedSubscription(id); ensureDraft(subscription);
        if(new HashSet<>(r.startsAt()).size()!=r.startsAt().size()) throw new DomainException("Training dates must be distinct");
        int capacity=r.capacity()==null ? subscription.getCapacity() : r.capacity();
        List<Person> selectedPeople=validateMembers(r.members(), capacity);
        members.deleteBySubscription(subscription);
        sessions.deleteBySubscription(subscription);
        subscription.updateMetadata(r.title(), r.description());
        subscription.updateCapacity(capacity);
        r.startsAt().stream().sorted().forEach(startsAt -> sessions.save(new TrainingSession(subscription, startsAt)));
        for(int index=0; index<r.members().size(); index++) { Api.MemberRequest member=r.members().get(index); members.save(new SubscriptionMember(subscription, selectedPeople.get(index), member.usesSkirt(), member.usesPaddle(), member.usesKayak())); }
        return subscriptionView(subscription);
    }
    @Transactional public Api.FinalizedSubscriptionEditPreviewView previewFinalizedSubscriptionEdit(long id, Api.FinalizedSubscriptionUpdateRequest r) {
        Subscription subscription=lockedSubscription(id); ensureFinalized(subscription);
        if (!finalizedStructureChanged(subscription, r)) return new Api.FinalizedSubscriptionEditPreviewView(false, List.of());
        FinalizedUpdatePlan plan=finalizedUpdatePlan(r);
        List<Api.AllocatedPaymentWarningView> warnings=allocatedPaymentWarnings(subscription, plan);
        return new Api.FinalizedSubscriptionEditPreviewView(!warnings.isEmpty(), warnings);
    }
    @Transactional public Api.SubscriptionView updateFinalizedSubscription(long id, Api.FinalizedSubscriptionUpdateRequest r) {
        Subscription subscription=lockedSubscription(id); ensureFinalized(subscription);
        if (!finalizedStructureChanged(subscription, r)) {
            if (r.members().size()>r.capacity()) throw new DomainException("Subscription capacity has been reached");
            subscription.updateMetadata(r.title(), r.description()); subscription.updateCapacity(r.capacity());
            return subscriptionView(subscription);
        }
        if (attendance.existsByTrainingSessionSubscription(subscription)) throw new DomainException("Cannot change schedule or members after attendance has been recorded");
        FinalizedUpdatePlan plan=finalizedUpdatePlan(r);
        List<Api.AllocatedPaymentWarningView> warnings=allocatedPaymentWarnings(subscription, plan);
        if (!warnings.isEmpty() && !r.confirmAllocatedPayments()) throw new DomainException("Allocated payments would be affected; review them and confirm the edit");
        applyFinalizedUpdate(subscription, r, plan);
        return subscriptionView(subscription);
    }
    @Transactional public Api.AttendanceView markSubscriptionAttendance(long id,Api.AttendanceRequest r) {
        TrainingSession session=get(sessions,id,"Training session"); Subscription s=lockedSubscription(session.getSubscription().getId()); if(s.getStatus()!=SubscriptionStatus.FINALIZED) throw new DomainException("Finalize subscription before taking attendance"); if(r.status()==AttendanceStatus.PLANNED) throw new DomainException("Attendance must be PRESENT or ABSENT");
        Person p=get(people,r.personId(),"Person"); SubscriptionMember member=members.findBySubscriptionAndPerson(s,p).orElseThrow(()->new DomainException("Person is not a member of this session's subscription")); Attendance entry=attendance.findByTrainingSessionAndPerson(session,p).orElseGet(()->new Attendance(session,p,member,AttendanceType.SUBSCRIPTION,r.status()));
        if(entry.getStatus()==AttendanceStatus.ABSENT&&r.status()==AttendanceStatus.PRESENT&&dropInsConsumeAllAbsences(session)) throw new DomainException("Cannot mark participant present: all released places are occupied by drop-in visitors"); entry.setStatus(r.status()); return attendanceView(attendance.save(entry));
    }
    @Transactional public Api.AttendanceView addDropIn(long id,Api.DropInRequest r) {
        TrainingSession session=get(sessions,id,"Training session"); Subscription s=lockedSubscription(session.getSubscription().getId()); if(s.getStatus()!=SubscriptionStatus.FINALIZED) throw new DomainException("Finalize subscription before admitting drop-in visitors"); Person p=get(people,r.personId(),"Person"); if(attendance.findByTrainingSessionAndPerson(session,p).isPresent()) throw new DomainException("Person already has attendance for this session"); if(dropInsConsumeAllAbsences(session)) throw new DomainException("No marked absence has released a place for a drop-in visitor");
        Attendance entry=attendance.save(new Attendance(session,p,null,AttendanceType.DROP_IN,AttendanceStatus.PRESENT)); LocalDate date=session.getStartsAt().toLocalDate(); long amount=applicableAmount(RateCode.DROP_IN,date); if(amount>0) charges.save(new Charge(p,s,entry,ChargeType.DROP_IN,amount,date,"Разовая тренировка")); return attendanceView(entry);
    }
    public Api.RosterView roster(long id) {
        TrainingSession session=get(sessions,id,"Training session"); List<Attendance> recorded=attendance.findByTrainingSessionOrderByTypeAscPersonFullNameAsc(session); Map<Long,Attendance> marked=recorded.stream().filter(a->a.getType()==AttendanceType.SUBSCRIPTION).collect(Collectors.toMap(a->a.getPerson().getId(),a->a)); List<Api.AttendanceView> entries=new ArrayList<>();
        for(SubscriptionMember m:members.findBySubscription(session.getSubscription())) { Attendance a=marked.get(m.getPerson().getId()); entries.add(a==null?new Api.AttendanceView(null,m.getPerson().getId(),m.getPerson().getFullName(),AttendanceType.SUBSCRIPTION,AttendanceStatus.PLANNED):attendanceView(a)); } recorded.stream().filter(a->a.getType()==AttendanceType.DROP_IN).map(this::attendanceView).forEach(entries::add); entries.sort(Comparator.comparing(Api.AttendanceView::type).thenComparing(Api.AttendanceView::personName)); return new Api.RosterView(id,session.getSubscription().getCapacity(),Math.max(0,absences(session)-visitors(session)),entries);
    }
    @Transactional public Api.ChargeView createManualCharge(Api.ManualChargeRequest r) { Person p=get(people,r.personId(),"Person"); Subscription s=r.subscriptionId()==null?null:get(subscriptions,r.subscriptionId(),"Subscription"); if(s!=null && members.findBySubscriptionAndPerson(s,p).isEmpty()) throw new DomainException("Person is not a member of this subscription"); return chargeView(charges.save(new Charge(p,s,null,ChargeType.MANUAL,r.amount(),r.chargedAt(),r.description()))); }
    @Transactional public Api.ChargeView editCharge(long id,Api.ChargeEditRequest r) { Charge c=lockedCharge(id); if(charges.allocatedFor(c)>r.amount()) throw new DomainException("Charge amount cannot be below allocated payment amount"); c.edit(r.amount(),r.chargedAt(),r.description()); syncEditedAutomaticCharge(c); return chargeView(c); }
    public Api.PaymentView createPayment(Api.PaymentRequest r) { Person p=get(people,r.personId(),"Person"); return paymentView(payments.save(new Payment(p,r.amount(),r.paidAt(),r.method(),r.note()))); }
    @Transactional public Api.PaymentView createPaymentWithAllocation(Api.PaymentWithAllocationRequest r) {
        if ((r.chargeId()==null) != (r.allocationAmount()==null)) throw new DomainException("Choose both a charge and an allocation amount, or neither");
        Person person=get(people,r.personId(),"Person");
        Charge charge = r.chargeId()==null ? null : lockedCharge(r.chargeId());
        if (charge!=null) validateAllocation(person, r.amount(), charge, r.allocationAmount());
        Payment payment=payments.save(new Payment(person,r.amount(),r.paidAt(),r.method(),r.note()));
        if (charge!=null) allocations.save(new PaymentAllocation(payment,charge,r.allocationAmount()));
        return paymentView(payment);
    }
    @Transactional public void allocatePayment(long id,Api.AllocationRequest r) { Payment p=lockedPayment(id); Charge c=lockedCharge(r.chargeId()); if(!p.getPerson().getId().equals(c.getPerson().getId())) throw new DomainException("Payment and charge must belong to the same person"); if(payments.allocatedFor(p)+r.amount()>p.getAmount()) throw new DomainException("Allocation exceeds payment amount"); if(charges.allocatedFor(c)+r.amount()>c.getAmount()) throw new DomainException("Allocation exceeds charge amount"); allocations.save(new PaymentAllocation(p,c,r.amount())); }
    public Api.BalanceView balance(long id) { Person p=get(people,id,"Person"); List<Charge> c=charges.findByPerson(p); List<Payment> paymentList=payments.findByPerson(p); long total=c.stream().mapToLong(Charge::getAmount).sum(), paid=paymentList.stream().mapToLong(Payment::getAmount).sum(); return new Api.BalanceView(id,total,paid,total-paid,c.stream().map(this::chargeView).toList(),paymentList.stream().map(this::paymentView).toList()); }
    private void validateAllocation(Person person, long paymentAmount, Charge charge, long allocationAmount) { if(!person.getId().equals(charge.getPerson().getId())) throw new DomainException("Payment and charge must belong to the same person"); if(allocationAmount>paymentAmount) throw new DomainException("Allocation exceeds payment amount"); if(charges.allocatedFor(charge)+allocationAmount>charge.getAmount()) throw new DomainException("Allocation exceeds charge amount"); }
    private Payment lockedPayment(long id) { return payments.findWithLockById(id).orElseThrow(()->new NoSuchElementException("Payment "+id+" was not found")); }
    private Charge lockedCharge(long id) { return charges.findWithLockById(id).orElseThrow(()->new NoSuchElementException("Charge "+id+" was not found")); }
    private record ChargeKey(Long personId, ChargeType type) { }
    private record MemberSettings(boolean usesSkirt, boolean usesPaddle, boolean usesKayak) { }
    private record FinalizedUpdatePlan(List<Person> people, Map<ChargeKey, Long> fees, long poolRentalAmount, LocalDate chargedAt) { }

    private FinalizedUpdatePlan finalizedUpdatePlan(Api.FinalizedSubscriptionUpdateRequest request) {
        if (new HashSet<>(request.startsAt()).size()!=request.startsAt().size()) throw new DomainException("Training dates must be distinct");
        List<Person> selectedPeople=validateMembers(request.members(), request.capacity());
        List<LocalDateTime> startsAt=request.startsAt().stream().sorted().toList();
        long poolRentalAmount=startsAt.stream().mapToLong(value -> applicableAmount(RateCode.POOL_RENTAL, value.toLocalDate())).sum();
        long poolShare=startsAt.stream().mapToLong(value -> ceilDiv(applicableAmount(RateCode.POOL_RENTAL, value.toLocalDate()), selectedPeople.size())).sum();
        Map<ChargeKey, Long> fees=new HashMap<>();
        for (int index=0; index<request.members().size(); index++) {
            Api.MemberRequest member=request.members().get(index); Long personId=selectedPeople.get(index).getId();
            fees.put(new ChargeKey(personId, ChargeType.POOL_SHARE), poolShare);
            fees.put(new ChargeKey(personId, ChargeType.SKIRT_RENT), member.usesSkirt() ? sumRateAt(startsAt, RateCode.SKIRT_RENTAL) : 0);
            fees.put(new ChargeKey(personId, ChargeType.PADDLE_RENT), member.usesPaddle() ? sumRateAt(startsAt, RateCode.PADDLE_RENTAL) : 0);
            fees.put(new ChargeKey(personId, ChargeType.KAYAK_RENT), member.usesKayak() ? sumRateAt(startsAt, RateCode.KAYAK_RENTAL) : 0);
        }
        return new FinalizedUpdatePlan(selectedPeople, fees, poolRentalAmount, startsAt.getFirst().toLocalDate());
    }
    private List<Api.AllocatedPaymentWarningView> allocatedPaymentWarnings(Subscription subscription, FinalizedUpdatePlan plan) {
        List<Charge> affected=charges.findBySubscription(subscription).stream().filter(this::automaticSubscriptionCharge).filter(charge -> automaticChargeWillChange(charge, plan)).toList();
        if (affected.isEmpty()) return List.of();
        return allocations.findByChargeIn(affected).stream().map(allocation -> new Api.AllocatedPaymentWarningView(allocation.getPayment().getId(), allocation.getPayment().getPaidAt(), allocation.getPayment().getAmount(), allocation.getAmount(), allocation.getCharge().getId(), allocation.getCharge().getDescription())).toList();
    }
    private boolean automaticChargeWillChange(Charge charge, FinalizedUpdatePlan plan) {
        Long desired=plan.fees().get(new ChargeKey(charge.getPerson().getId(), charge.getType()));
        if (desired==null || desired==0) return true;
        long amount=Math.max(desired, charges.allocatedFor(charge));
        return charge.getAmount()!=amount || !charge.getChargedAt().equals(plan.chargedAt());
    }
    private void applyFinalizedUpdate(Subscription subscription, Api.FinalizedSubscriptionUpdateRequest request, FinalizedUpdatePlan plan) {
        Map<ChargeKey, Long> actualFees=new HashMap<>(); Set<ChargeKey> consumed=new HashSet<>();
        for (Charge charge : charges.findBySubscription(subscription).stream().filter(this::automaticSubscriptionCharge).toList()) {
            ChargeKey key=new ChargeKey(charge.getPerson().getId(), charge.getType()); Long desired=consumed.add(key) ? plan.fees().get(key) : null; long allocated=charges.allocatedFor(charge);
            if (desired==null || desired==0) {
                if (allocated==0) { charges.delete(charge); continue; }
                actualFees.put(key, charge.getAmount());
                continue;
            }
            long amount=Math.max(desired, allocated); charge.edit(amount, plan.chargedAt(), charge.getDescription()); actualFees.put(key, amount);
        }
        for (Map.Entry<ChargeKey, Long> entry : plan.fees().entrySet()) {
            if (entry.getValue()==0 || actualFees.containsKey(entry.getKey())) continue;
            ChargeKey key=entry.getKey(); charges.save(new Charge(get(people, key.personId(), "Person"), subscription, null, key.type(), entry.getValue(), plan.chargedAt(), automaticChargeDescription(key.type()))); actualFees.put(key, entry.getValue());
        }
        sessions.deleteBySubscription(subscription); members.deleteBySubscription(subscription);
        subscription.updateMetadata(request.title(), request.description()); subscription.updateCapacity(request.capacity()); subscription.updatePoolRentalAmount(plan.poolRentalAmount());
        request.startsAt().stream().sorted().forEach(startsAt -> sessions.save(new TrainingSession(subscription, startsAt)));
        for (int index=0; index<request.members().size(); index++) {
            Api.MemberRequest member=request.members().get(index); Person person=plan.people().get(index); SubscriptionMember saved=members.save(new SubscriptionMember(subscription, person, member.usesSkirt(), member.usesPaddle(), member.usesKayak()));
            saved.snapshotFees(actualFees.getOrDefault(new ChargeKey(person.getId(), ChargeType.POOL_SHARE), 0L), actualFees.getOrDefault(new ChargeKey(person.getId(), ChargeType.SKIRT_RENT), 0L), actualFees.getOrDefault(new ChargeKey(person.getId(), ChargeType.PADDLE_RENT), 0L), actualFees.getOrDefault(new ChargeKey(person.getId(), ChargeType.KAYAK_RENT), 0L));
        }
    }
    private boolean finalizedStructureChanged(Subscription subscription, Api.FinalizedSubscriptionUpdateRequest request) {
        Set<LocalDateTime> currentSessions=sessionsFor(subscription).stream().map(TrainingSession::getStartsAt).collect(Collectors.toSet());
        if (!currentSessions.equals(new HashSet<>(request.startsAt()))) return true;
        Map<Long, MemberSettings> currentMembers=members.findBySubscription(subscription).stream().collect(Collectors.toMap(member -> member.getPerson().getId(), member -> new MemberSettings(member.isUsesSkirt(), member.isUsesPaddle(), member.isUsesKayak())));
        Map<Long, MemberSettings> requestedMembers=new HashMap<>();
        for (Api.MemberRequest member : request.members()) {
            if (requestedMembers.put(member.personId(), new MemberSettings(member.usesSkirt(), member.usesPaddle(), member.usesKayak()))!=null) return true;
        }
        return !currentMembers.equals(requestedMembers);
    }
    private Api.PersonFinancialReportView personReport(Person person, List<Charge> personCharges, List<Payment> personPayments, LocalDate from, LocalDate to) {
        long chargedInPeriod=personCharges.stream().filter(charge -> inPeriod(charge.getChargedAt(), from, to)).mapToLong(Charge::getAmount).sum();
        long receivedInPeriod=personPayments.stream().filter(payment -> inPeriod(payment.getPaidAt(), from, to)).mapToLong(Payment::getAmount).sum();
        long chargedThroughEnd=personCharges.stream().filter(charge -> !charge.getChargedAt().isAfter(to)).mapToLong(Charge::getAmount).sum();
        long paidThroughEnd=personPayments.stream().filter(payment -> !payment.getPaidAt().isAfter(to)).mapToLong(Payment::getAmount).sum();
        LocalDate lastPayment=personPayments.stream().map(Payment::getPaidAt).filter(date -> !date.isAfter(to)).max(Comparator.naturalOrder()).orElse(null);
        return new Api.PersonFinancialReportView(person.getId(), person.getFullName(), person.isActive(), chargedInPeriod, receivedInPeriod, chargedThroughEnd-paidThroughEnd, lastPayment);
    }
    private Api.PersonFinancialReportView subscriptionPersonReport(Person person, List<Charge> personCharges, List<PaymentAllocation> personAllocations, LocalDate from, LocalDate to) {
        long chargedInPeriod=personCharges.stream().filter(charge -> inPeriod(charge.getChargedAt(), from, to)).mapToLong(Charge::getAmount).sum();
        long receivedInPeriod=personAllocations.stream().filter(allocation -> inPeriod(allocation.getAllocatedAt(), from, to)).mapToLong(PaymentAllocation::getAmount).sum();
        long chargedThroughEnd=personCharges.stream().filter(charge -> !charge.getChargedAt().isAfter(to)).mapToLong(Charge::getAmount).sum();
        List<PaymentAllocation> allocationsThroughEnd=personAllocations.stream().filter(allocation -> !allocation.getAllocatedAt().isAfter(to) && !allocation.getPayment().getPaidAt().isAfter(to) && !allocation.getCharge().getChargedAt().isAfter(to)).toList();
        long paidThroughEnd=allocationsThroughEnd.stream().mapToLong(PaymentAllocation::getAmount).sum();
        LocalDate lastPayment=allocationsThroughEnd.stream().map(allocation -> allocation.getPayment().getPaidAt()).max(Comparator.naturalOrder()).orElse(null);
        return new Api.PersonFinancialReportView(person.getId(), person.getFullName(), person.isActive(), chargedInPeriod, receivedInPeriod, chargedThroughEnd-paidThroughEnd, lastPayment);
    }
    private Api.SubscriptionFinancialReportView subscriptionReport(Subscription subscription, List<Charge> subscriptionCharges, long allocatedInPeriod, long allocatedThroughEnd, List<SubscriptionMember> subscriptionMembers, List<TrainingSession> subscriptionSessions, LocalDate from, LocalDate to) {
        List<LocalDate> sessionDates=subscriptionSessions.stream().map(session -> session.getStartsAt().toLocalDate()).sorted().toList();
        LocalDate first=sessionDates.isEmpty() ? null : sessionDates.getFirst(), last=sessionDates.isEmpty() ? null : sessionDates.getLast();
        long chargedInPeriod=subscriptionCharges.stream().filter(charge -> inPeriod(charge.getChargedAt(), from, to)).mapToLong(Charge::getAmount).sum();
        long chargedThroughEnd=subscriptionCharges.stream().filter(charge -> !charge.getChargedAt().isAfter(to)).mapToLong(Charge::getAmount).sum();
        long poolRentalCostAtEnd=poolRentalCostAtEnd(subscription, subscriptionSessions, to);
        return new Api.SubscriptionFinancialReportView(subscription.getId(), subscription.getTitle(), subscription.getStatus(), first, last, subscriptionMembers.size(), chargedInPeriod, allocatedInPeriod, chargedThroughEnd-allocatedThroughEnd, allocatedThroughEnd, poolRentalCostAtEnd, allocatedThroughEnd-poolRentalCostAtEnd);
    }
    private long poolRentalCostAtEnd(Subscription subscription, List<TrainingSession> subscriptionSessions, LocalDate to) {
        LocalDate firstSessionDate=subscriptionSessions.stream().map(session -> session.getStartsAt().toLocalDate()).min(Comparator.naturalOrder()).orElse(null);
        return firstSessionDate!=null && !firstSessionDate.isAfter(to) ? Objects.requireNonNullElse(subscription.getPoolRentalAmount(), 0L) : 0L;
    }
    private boolean inPeriod(LocalDate value, LocalDate from, LocalDate to) { return !value.isBefore(from) && !value.isAfter(to); }
    private boolean overlapsPeriod(LocalDate first, LocalDate last, LocalDate from, LocalDate to) { return first!=null && last!=null && !last.isBefore(from) && !first.isAfter(to); }
    private boolean automaticSubscriptionCharge(Charge charge) { return charge.getType()!=ChargeType.MANUAL && charge.getType()!=ChargeType.DROP_IN; }
    private String automaticChargeDescription(ChargeType type) { return switch (type) { case POOL_SHARE -> "Доля аренды бассейна за абонемент"; case SKIRT_RENT -> "Аренда юбки за абонемент"; case PADDLE_RENT -> "Аренда весла за абонемент"; case KAYAK_RENT -> "Аренда каяка за абонемент"; default -> throw new IllegalArgumentException("Charge type does not belong to a subscription member fee"); }; }
    private long sumRate(List<TrainingSession> ss,RateCode c){return ss.stream().mapToLong(s->applicableAmount(c,s.getStartsAt().toLocalDate())).sum();} private long sumRateAt(List<LocalDateTime> startsAt, RateCode code) { return startsAt.stream().mapToLong(value -> applicableAmount(code, value.toLocalDate())).sum(); } private boolean dropInsConsumeAllAbsences(TrainingSession s){return visitors(s)>=absences(s);} private long absences(TrainingSession s){return attendance.countByTrainingSessionAndTypeAndStatus(s,AttendanceType.SUBSCRIPTION,AttendanceStatus.ABSENT);} private long visitors(TrainingSession s){return attendance.countByTrainingSessionAndTypeAndStatus(s,AttendanceType.DROP_IN,AttendanceStatus.PRESENT);} private List<TrainingSession> sessionsFor(Subscription s){return sessions.findAll().stream().filter(x->x.getSubscription().getId().equals(s.getId())).sorted(Comparator.comparing(TrainingSession::getStartsAt)).toList();} private void syncEditedAutomaticCharge(Charge c){if(c.getSubscription()==null||c.getType()==ChargeType.MANUAL||c.getType()==ChargeType.DROP_IN)return; members.findBySubscriptionAndPerson(c.getSubscription(),c.getPerson()).ifPresent(member -> member.editAutomaticFee(c.getType(),c.getAmount()));} private Subscription lockedSubscription(long id){return subscriptions.findWithLockById(id).orElseThrow(()->new NoSuchElementException("Subscription "+id+" was not found"));} private void ensureFinalized(Subscription s) { if (s.getStatus()!=SubscriptionStatus.FINALIZED) throw new DomainException("Only a finalized subscription can be fully changed"); } private long applicableAmount(RateCode c,LocalDate d){return rates.applicable(c,d).stream().findFirst().orElseThrow(()->new DomainException("No active "+c+" rate on "+d)).getAmount();} private static boolean overlaps(LocalDate af,LocalDate at,LocalDate bf,LocalDate bt){return(at==null||!at.isBefore(bf))&&(bt==null||!bt.isBefore(af));} private static long ceilDiv(long v,long d){return(v+d-1)/d;} private List<Person> validateMembers(List<Api.MemberRequest> requestedMembers, int capacity){if(requestedMembers.isEmpty()) throw new DomainException("Subscription must have at least one member"); if(requestedMembers.size()>capacity) throw new DomainException("Subscription capacity has been reached"); Set<Long> personIds=new HashSet<>(); List<Person> selectedPeople=new ArrayList<>(); for(Api.MemberRequest member:requestedMembers){if(!personIds.add(member.personId())) throw new DomainException("Person is already in this subscription"); Person person=get(people,member.personId(),"Person"); if(!person.isActive()) throw new DomainException("Inactive person cannot join a subscription"); selectedPeople.add(person);} return selectedPeople;} private void ensureDraft(Subscription s){if(s.getStatus()!=SubscriptionStatus.DRAFT)throw new DomainException("Only a draft subscription can be changed");} private <T>T get(org.springframework.data.jpa.repository.JpaRepository<T,Long> r,long id,String label){return r.findById(id).orElseThrow(()->new NoSuchElementException(label+" "+id+" was not found"));}
    private Api.PersonView personView(Person p) { return new Api.PersonView(p.getId(), p.getFullName(), p.getPhone(), p.getNote(), p.isActive()); }
    private Api.RateView rateView(PricingRate r) { return new Api.RateView(r.getId(), r.getCode(), r.getAmount(), r.getEffectiveFrom(), r.getEffectiveTo()); }
    private Api.MemberView memberView(SubscriptionMember m) { return new Api.MemberView(m.getId(), m.getPerson().getId(), m.getPerson().getFullName(), m.isUsesSkirt(), m.isUsesPaddle(), m.isUsesKayak(), m.getPoolShare(), m.getSkirtFee(), m.getPaddleFee(), m.getKayakFee()); }
    private Api.SessionView sessionView(TrainingSession s) { return new Api.SessionView(s.getId(), s.getStartsAt()); }
    private Api.SubscriptionView subscriptionView(Subscription s) { return subscriptionView(s, members.findBySubscription(s), sessionsFor(s)); }
    private Api.SubscriptionView subscriptionView(Subscription s, List<SubscriptionMember> subscriptionMembers, List<TrainingSession> subscriptionSessions) { return new Api.SubscriptionView(s.getId(), s.getCapacity(), s.getTitle(), s.getDescription(), s.getStatus(), s.getPoolRentalAmount(), subscriptionMembers.stream().map(this::memberView).toList(), subscriptionSessions.stream().sorted(Comparator.comparing(TrainingSession::getStartsAt)).map(this::sessionView).toList()); }
    private Api.AttendanceView attendanceView(Attendance a) { return new Api.AttendanceView(a.getId(), a.getPerson().getId(), a.getPerson().getFullName(), a.getType(), a.getStatus()); }
    private Api.PaymentView paymentView(Payment payment) {
        List<Api.PaymentAllocationView> paymentAllocations = allocations.findByPayment(payment).stream()
                .map(allocation -> new Api.PaymentAllocationView(allocation.getId(), allocation.getCharge().getId(), allocation.getCharge().getDescription(), allocation.getAmount()))
                .toList();
        return new Api.PaymentView(payment.getId(), payment.getPerson().getId(), payment.getAmount(), payment.getPaidAt(), payment.getMethod(), payment.getNote(), paymentAllocations);
    }
    private Api.ChargeView chargeView(Charge c) { return new Api.ChargeView(c.getId(), c.getType(), c.getAmount(), charges.allocatedFor(c), c.getChargedAt(), c.getDescription(), c.getSubscription()==null?null:c.getSubscription().getId(), c.getAttendance()==null?null:c.getAttendance().getId()); }
}
