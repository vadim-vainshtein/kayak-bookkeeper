package kz.kayak.bookkeeper.api.dto;


public record PersonView(
        Long id,
        String fullName,
        String phone,
        String note,
        boolean active
) {
}
