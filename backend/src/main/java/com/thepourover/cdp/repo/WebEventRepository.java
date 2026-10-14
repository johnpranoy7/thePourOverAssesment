package com.thepourover.cdp.repo;

import com.thepourover.cdp.domain.Subscriber;
import com.thepourover.cdp.domain.WebEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WebEventRepository extends JpaRepository<WebEvent, Long> {
    List<WebEvent> findBySubscriberOrderByTimestampAsc(Subscriber subscriber);

    List<WebEvent> findByVisitorId(String visitorId);

    long countBySubscriberIsNotNull();

    /** Attach anonymous (unlinked) events for a visitor; does not reassign already-linked rows. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update WebEvent w
            set w.subscriber = :subscriber
            where w.visitorId = :visitorId and w.subscriber is null
            """)
    int linkAnonymousVisitorEvents(
            @Param("visitorId") String visitorId, @Param("subscriber") Subscriber subscriber);

    /** Bulk engagement: [subscriberId, visitCount] for linked web events. */
    @Query("""
            select w.subscriber.id, count(w)
            from WebEvent w
            where w.subscriber is not null
            group by w.subscriber.id
            """)
    List<Object[]> countGroupedBySubscriber();

    @Query("""
            select distinct w.subscriber.id
            from WebEvent w
            where w.subscriber is not null
            """)
    List<Long> findDistinctSubscriberIdsWithVisits();
}
