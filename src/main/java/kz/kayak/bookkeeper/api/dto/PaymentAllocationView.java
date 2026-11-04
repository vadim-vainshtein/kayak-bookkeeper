package kz.kayak.bookkeeper.api.dto;


public record PaymentAllocationView(
        Long id,
        Long chargeId,
        String chargeDescription,
        long amount
) {
}
