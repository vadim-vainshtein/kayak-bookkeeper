package kz.kayak.bookkeeper.service;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import kz.kayak.bookkeeper.domain.Subscription;
import kz.kayak.bookkeeper.domain.SubscriptionStatus;
import kz.kayak.bookkeeper.domain.TrainingSession;
import kz.kayak.bookkeeper.repository.SubscriptionRepository;
import kz.kayak.bookkeeper.repository.TrainingSessionRepository;
import org.springframework.stereotype.Service;

/** Loads subscription schedules and enforces lifecycle preconditions. */
@Service
public class SubscriptionAccess {
    private final SubscriptionRepository subscriptions;
    private final TrainingSessionRepository sessions;

    public SubscriptionAccess(SubscriptionRepository subscriptions, TrainingSessionRepository sessions) {
        this.subscriptions = subscriptions;
        this.sessions = sessions;
    }

    public Subscription lockedSubscription(long id) {
        return subscriptions.findWithLockById(id).orElseThrow(() -> new NoSuchElementException("Subscription " + id + " was not found"));
    }

    public List<TrainingSession> sessionsFor(Subscription s) {
        return sessions.findAll().stream().filter(x -> x.getSubscription().getId().equals(s.getId())).sorted(Comparator.comparing(TrainingSession::getStartsAt)).toList();
    }

    public void ensureDraft(Subscription s) {
        if (s.getStatus() != SubscriptionStatus.DRAFT)
            throw new DomainException("Only a draft subscription can be changed");
    }

    public void ensureFinalized(Subscription s) {
        if (s.getStatus() != SubscriptionStatus.FINALIZED)
            throw new DomainException("Only a finalized subscription can be fully changed");
    }
}
