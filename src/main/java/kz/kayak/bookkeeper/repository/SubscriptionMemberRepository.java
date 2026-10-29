package kz.kayak.bookkeeper.repository;
import kz.kayak.bookkeeper.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface SubscriptionMemberRepository extends JpaRepository<SubscriptionMember, Long> {
    List<SubscriptionMember> findBySubscription(Subscription subscription);
    @Query("select m from SubscriptionMember m join fetch m.subscription join fetch m.person where m.subscription in :subscriptions")
    List<SubscriptionMember> findBySubscriptionInWithSubscriptionAndPerson(@Param("subscriptions") Collection<Subscription> subscriptions);
    Optional<SubscriptionMember> findBySubscriptionAndPerson(Subscription subscription, Person person);
    long countBySubscription(Subscription subscription);
}
