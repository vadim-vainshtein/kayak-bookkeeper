package kz.kayak.bookkeeper.api.dto;


public record MemberView(
        Long id,
        Long personId,
        String personName,
        boolean usesSkirt,
        boolean usesPaddle,
        boolean usesKayak,
        Long poolShare,
        Long skirtFee,
        Long paddleFee,
        Long kayakFee
) {
}
