package kz.kayak.bookkeeper.api.dto;

import kz.kayak.bookkeeper.domain.UserRole;

public record UserAccountView(Long id, String login, UserRole role) { }
