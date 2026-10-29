package kz.kayak.bookkeeper.repository;
import kz.kayak.bookkeeper.domain.Person;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PersonRepository extends JpaRepository<Person, Long> { }
