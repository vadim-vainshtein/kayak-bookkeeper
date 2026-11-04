package kz.kayak.bookkeeper.service;

import jakarta.transaction.Transactional;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import kz.kayak.bookkeeper.api.dto.DraftSubscriptionUpdateRequest;
import kz.kayak.bookkeeper.api.dto.ExplicitSubscriptionRequest;
import kz.kayak.bookkeeper.api.dto.MemberRequest;
import kz.kayak.bookkeeper.api.dto.RecurringSubscriptionRequest;
import kz.kayak.bookkeeper.api.dto.RecurringSubscriptionWithMembersRequest;
import kz.kayak.bookkeeper.api.dto.SubscriptionMetadataUpdateRequest;
import kz.kayak.bookkeeper.api.dto.SubscriptionView;
import kz.kayak.bookkeeper.domain.Person;
import kz.kayak.bookkeeper.domain.Subscription;
import kz.kayak.bookkeeper.domain.SubscriptionMember;
import kz.kayak.bookkeeper.domain.SubscriptionStatus;
import kz.kayak.bookkeeper.domain.TrainingSession;
import kz.kayak.bookkeeper.repository.PersonRepository;
import kz.kayak.bookkeeper.repository.SubscriptionMemberRepository;
import kz.kayak.bookkeeper.repository.SubscriptionRepository;
import kz.kayak.bookkeeper.repository.TrainingSessionRepository;
import org.springframework.stereotype.Service;

import static kz.kayak.bookkeeper.service.EntityLookup.get;

/** Creates and edits drafts, maintains metadata, and completes subscriptions. */
@Service
public class SubscriptionService {
    private final PersonRepository people;
    private final SubscriptionRepository subscriptions;
    private final TrainingSessionRepository sessions;
    private final SubscriptionMemberRepository members;
    private final SubscriptionAccess access;
    private final SubscriptionQueryService views;
    private final SubscriptionMemberSelection memberSelection;

    public SubscriptionService(
            PersonRepository people,
            SubscriptionRepository subscriptions,
            TrainingSessionRepository sessions,
            SubscriptionMemberRepository members,
            SubscriptionAccess access,
            SubscriptionQueryService views,
            SubscriptionMemberSelection memberSelection) {
        this.people = people;
        this.subscriptions = subscriptions;
        this.sessions = sessions;
        this.members = members;
        this.access = access;
        this.views = views;
        this.memberSelection = memberSelection;
    }

    @Transactional
    public SubscriptionView createExplicitSubscription(ExplicitSubscriptionRequest r) {
        if (new HashSet<>(r.startsAt()).size() != r.startsAt().size())
            throw new DomainException("Training dates must be distinct");
        return createSubscription(r.startsAt().stream().sorted().toList(), r.capacity(), r.title(), r.description());
    }

    @Transactional
    public SubscriptionView createRecurringSubscription(RecurringSubscriptionRequest r) {
        return createSubscription(recurringDates(r.startDate(), r.dayOfWeek(), r.startTime(), r.sessionCount()),
                r.capacity(), r.title(), r.description());
    }

    @Transactional
    public SubscriptionView createRecurringSubscriptionWithMembers(RecurringSubscriptionWithMembersRequest r) {
        List<Person> selectedPeople = memberSelection.validateMembers(r.members(), r.capacity() == null ? 8 : r.capacity());
        List<LocalDateTime> dates = recurringDates(r.startDate(), r.dayOfWeek(), r.startTime(), r.sessionCount());
        Subscription subscription = subscriptions.save(new Subscription(r.capacity(), r.title(), r.description()));
        dates.forEach(date -> sessions.save(new TrainingSession(subscription, date)));
        for (int i = 0; i < r.members().size(); i++) {
            MemberRequest member = r.members().get(i);
            members.save(new SubscriptionMember(subscription, selectedPeople.get(i), member.usesSkirt(), member.usesPaddle(), member.usesKayak()));
        }
        return views.subscriptionView(subscription);
    }

    private List<LocalDateTime> recurringDates(LocalDate startDate, DayOfWeek dayOfWeek, LocalTime startTime, int count) {
        LocalDate first = startDate.with(TemporalAdjusters.nextOrSame(dayOfWeek));
        List<LocalDateTime> dates = new ArrayList<>();
        for (int index = 0; index < count; index++) dates.add(LocalDateTime.of(first.plusWeeks(index), startTime));
        return dates;
    }

    private SubscriptionView createSubscription(List<LocalDateTime> dates, Integer capacity, String title, String description) {
        Subscription s = subscriptions.save(new Subscription(capacity, title, description));
        dates.forEach(d -> sessions.save(new TrainingSession(s, d)));
        return views.subscriptionView(s);
    }

    @Transactional
    public SubscriptionView addMember(long id, MemberRequest r) {
        Subscription s = access.lockedSubscription(id);
        access.ensureDraft(s);
        Person p = get(people, r.personId(), "Person");
        if (!p.isActive()) throw new DomainException("Inactive person cannot join a subscription");
        if (members.countBySubscription(s) >= s.getCapacity())
            throw new DomainException("Subscription capacity has been reached");
        if (members.findBySubscriptionAndPerson(s, p).isPresent())
            throw new DomainException("Person is already in this subscription");
        members.save(new SubscriptionMember(s, p, r.usesSkirt(), r.usesPaddle(), r.usesKayak()));
        return views.subscriptionView(s);
    }

    @Transactional
    public SubscriptionView completeSubscription(long id) {
        Subscription s = access.lockedSubscription(id);
        if (s.getStatus() != SubscriptionStatus.FINALIZED)
            throw new DomainException("Only a finalized subscription can be completed");
        s.complete();
        return views.subscriptionView(s);
    }

    @Transactional
    public SubscriptionView updateSubscriptionMetadata(long id, SubscriptionMetadataUpdateRequest r) {
        Subscription subscription = access.lockedSubscription(id);
        subscription.updateMetadata(r.title(), r.description());
        return views.subscriptionView(subscription);
    }

    @Transactional
    public SubscriptionView updateDraftSubscription(long id, DraftSubscriptionUpdateRequest r) {
        Subscription subscription = access.lockedSubscription(id);
        access.ensureDraft(subscription);
        if (new HashSet<>(r.startsAt()).size() != r.startsAt().size())
            throw new DomainException("Training dates must be distinct");
        int capacity = r.capacity() == null ? subscription.getCapacity() : r.capacity();
        List<Person> selectedPeople = memberSelection.validateMembers(r.members(), capacity);
        members.deleteBySubscription(subscription);
        sessions.deleteBySubscription(subscription);
        subscription.updateMetadata(r.title(), r.description());
        subscription.updateCapacity(capacity);
        r.startsAt().stream().sorted().forEach(startsAt -> sessions.save(new TrainingSession(subscription, startsAt)));
        for (int index = 0; index < r.members().size(); index++) {
            MemberRequest member = r.members().get(index);
            members.save(new SubscriptionMember(subscription, selectedPeople.get(index), member.usesSkirt(), member.usesPaddle(), member.usesKayak()));
        }
        return views.subscriptionView(subscription);
    }
}
