package kz.kayak.bookkeeper.repository;
import kz.kayak.bookkeeper.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    Optional<Attendance> findByTrainingSessionAndPerson(TrainingSession session, Person person);
    long countByTrainingSessionAndTypeAndStatus(TrainingSession session, AttendanceType type, AttendanceStatus status);
    List<Attendance> findByTrainingSessionOrderByTypeAscPersonFullNameAsc(TrainingSession session);
}
