package kz.kayak.bookkeeper.service;

import kz.kayak.bookkeeper.domain.ChargeType;

record MemberChargeKey(Long personId, ChargeType type) {
}
