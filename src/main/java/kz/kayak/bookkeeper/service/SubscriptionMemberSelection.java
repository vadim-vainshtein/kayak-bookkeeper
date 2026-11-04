package kz.kayak.bookkeeper.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import kz.kayak.bookkeeper.api.dto.MemberRequest;
import kz.kayak.bookkeeper.domain.Person;
import kz.kayak.bookkeeper.domain.Subscription;
import kz.kayak.bookkeeper.repository.PersonRepository;
import org.springframework.stereotype.Service;

import static kz.kayak.bookkeeper.service.EntityLookup.get;

/** Validates a proposed subscription membership before any replacement. */
@Service
public class SubscriptionMemberSelection {
    private final PersonRepository people;

    public SubscriptionMemberSelection(PersonRepository people) {
        this.people = people;
    }

    public List<Person> validateMembers(List<MemberRequest> requestedMembers, int capacity) {
        if (requestedMembers.isEmpty()) throw new DomainException("Subscription must have at least one member");
        if (requestedMembers.size() > capacity) throw new DomainException("Subscription capacity has been reached");
        Set<Long> personIds = new HashSet<>();
        List<Person> selectedPeople = new ArrayList<>();
        for (MemberRequest member : requestedMembers) {
            if (!personIds.add(member.personId())) throw new DomainException("Person is already in this subscription");
            Person person = get(people, member.personId(), "Person");
            if (!person.isActive()) throw new DomainException("Inactive person cannot join a subscription");
            selectedPeople.add(person);
        }
        return selectedPeople;
    }
}
