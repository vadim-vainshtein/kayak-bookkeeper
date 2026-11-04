package kz.kayak.bookkeeper.api.dto;

import java.util.List;
import kz.kayak.bookkeeper.domain.SubscriptionStatus;

public record SubscriptionView(
        Long id,
        int capacity,
        String title,
        String description,
        SubscriptionStatus status,
        Long poolRentalAmount,
        List<MemberView> members,
        List<SessionView> sessions
) {
}
