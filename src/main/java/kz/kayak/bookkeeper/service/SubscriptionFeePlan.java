package kz.kayak.bookkeeper.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import kz.kayak.bookkeeper.domain.Person;

record SubscriptionFeePlan(List<Person> people, Map<MemberChargeKey, Long> fees, long poolRentalAmount, LocalDate chargedAt) {
}
