package kz.kayak.bookkeeper.repository;
import kz.kayak.bookkeeper.domain.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface ChargeRepository extends JpaRepository<Charge, Long> {
    List<Charge> findByPerson(Person person);
    @Query("select coalesce(sum(a.amount), 0) from PaymentAllocation a where a.charge=:charge")
    long allocatedFor(Charge charge);
}
