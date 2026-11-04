package kz.kayak.bookkeeper.api.dto;

import java.util.List;

public record FinalizedSubscriptionEditPreviewView(
        boolean requiresConfirmation,
        List<AllocatedPaymentWarningView> allocatedPayments
) {
}
