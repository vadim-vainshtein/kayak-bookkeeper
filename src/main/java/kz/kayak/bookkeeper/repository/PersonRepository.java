package kz.kayak.bookkeeper.repository;
import kz.kayak.bookkeeper.domain.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
public interface PersonRepository extends JpaRepository<Person, Long> {
    @Query("select person from Person person order by lower(person.fullName), person.id")
    List<Person> findAllOrderedByFullNameIgnoreCase();
}
