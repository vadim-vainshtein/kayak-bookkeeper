package kz.kayak.bookkeeper.service;

import jakarta.transaction.Transactional;
import java.util.List;
import kz.kayak.bookkeeper.api.dto.PersonRequest;
import kz.kayak.bookkeeper.api.dto.PersonUpdateRequest;
import kz.kayak.bookkeeper.api.dto.PersonView;
import kz.kayak.bookkeeper.domain.Person;
import kz.kayak.bookkeeper.repository.PersonRepository;
import org.springframework.stereotype.Service;

import static kz.kayak.bookkeeper.service.EntityLookup.get;
import static kz.kayak.bookkeeper.service.EntityViews.personView;

/** Creates and maintains kayaker contact cards. */
@Service
public class PersonService {
    private final PersonRepository people;

    public PersonService(PersonRepository people) {
        this.people = people;
    }

    public PersonView createPerson(PersonRequest r) {
        return personView(people.save(new Person(r.fullName(), r.phone(), r.note())));
    }

    public List<PersonView> listPeople() {
        return people.findAllOrderedByFullNameIgnoreCase().stream().map(EntityViews::personView).toList();
    }

    @Transactional
    public PersonView updatePerson(long id, PersonUpdateRequest r) {
        Person person = get(people, id, "Person");
        person.update(r.fullName(), r.phone(), r.note(), r.active());
        return personView(person);
    }
}
