package kz.kayak.bookkeeper.service;

import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kz.kayak.bookkeeper.api.dto.AllocatedPaymentWarningView;
import kz.kayak.bookkeeper.api.dto.FinalizedSubscriptionEditPreviewView;
import kz.kayak.bookkeeper.api.dto.FinalizedSubscriptionUpdateRequest;
import kz.kayak.bookkeeper.api.dto.MemberRequest;
import kz.kayak.bookkeeper.api.dto.SubscriptionView;
import kz.kayak.bookkeeper.domain.Subscription;
import kz.kayak.bookkeeper.domain.TrainingSession;
import kz.kayak.bookkeeper.repository.SubscriptionMemberRepository;
import org.springframework.stereotype.Service;

/** Previews and applies finalized subscription edits in a single locked transaction. */
@Service
public class FinalizedSubscriptionEditService {
    private final SubscriptionMemberRepository members;
    private final SubscriptionAccess access;
    private final SubscriptionQueryService views;
    private final SubscriptionFeePlanner planner;
    private final SubscriptionChargeReconciler reconciler;

    public FinalizedSubscriptionEditService(
            SubscriptionMemberRepository members,
            SubscriptionAccess access,
            SubscriptionQueryService views,
            SubscriptionFeePlanner planner,
            SubscriptionChargeReconciler reconciler) {
        this.members = members;
        this.access = access;
        this.views = views;
        this.planner = planner;
        this.reconciler = reconciler;
    }

    @Transactional
    public FinalizedSubscriptionEditPreviewView previewFinalizedSubscriptionEdit(long id, FinalizedSubscriptionUpdateRequest r) {
        Subscription subscription = access.lockedSubscription(id);
        access.ensureFinalized(subscription);
        if (!finalizedStructureChanged(subscription, r))
            return new FinalizedSubscriptionEditPreviewView(false, List.of());
        SubscriptionFeePlan plan = planner.plan(r);
        List<AllocatedPaymentWarningView> warnings = reconciler.allocatedPaymentWarnings(subscription, plan);
        return new FinalizedSubscriptionEditPreviewView(!warnings.isEmpty(), warnings);
    }

    @Transactional
    public SubscriptionView updateFinalizedSubscription(long id, FinalizedSubscriptionUpdateRequest r) {
        Subscription subscription = access.lockedSubscription(id);
        access.ensureFinalized(subscription);
        if (!finalizedStructureChanged(subscription, r)) {
            if (r.members().size() > r.capacity()) throw new DomainException("Subscription capacity has been reached");
            subscription.updateMetadata(r.title(), r.description());
            subscription.updateCapacity(r.capacity());
            return views.subscriptionView(subscription);
        }
        SubscriptionFeePlan plan = planner.plan(r);
        List<AllocatedPaymentWarningView> warnings = reconciler.allocatedPaymentWarnings(subscription, plan);
        if (!warnings.isEmpty() && !r.confirmAllocatedPayments())
            throw new DomainException("Allocated payments would be affected; review them and confirm the edit");
        reconciler.applyFinalizedUpdate(subscription, r, plan);
        return views.subscriptionView(subscription);
    }

    private boolean finalizedStructureChanged(Subscription subscription, FinalizedSubscriptionUpdateRequest request) {
        Set<LocalDateTime> currentSessions = access.sessionsFor(subscription).stream().map(TrainingSession::getStartsAt).collect(Collectors.toSet());
        if (!currentSessions.equals(new HashSet<>(request.startsAt()))) return true;
        Map<Long, MemberSettings> currentMembers = members.findBySubscription(subscription).stream().collect(Collectors.toMap(member -> member.getPerson().getId(), member -> new MemberSettings(member.isUsesSkirt(), member.isUsesPaddle(), member.isUsesKayak())));
        Map<Long, MemberSettings> requestedMembers = new HashMap<>();
        for (MemberRequest member : request.members()) {
            if (requestedMembers.put(member.personId(), new MemberSettings(member.usesSkirt(), member.usesPaddle(), member.usesKayak())) != null)
                return true;
        }
        return !currentMembers.equals(requestedMembers);
    }

    private record MemberSettings(boolean usesSkirt, boolean usesPaddle, boolean usesKayak) {}
}
