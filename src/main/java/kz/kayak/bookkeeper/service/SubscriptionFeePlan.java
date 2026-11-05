package kz.kayak.bookkeeper.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import kz.kayak.bookkeeper.domain.Person;

record SubscriptionFeePlan(List<Person> people, Map<MemberChargeKey, Long> fees, long poolRentalAmount, Map<LocalDateTime, Long> sessionRentals, LocalDate chargedAt) {
}
