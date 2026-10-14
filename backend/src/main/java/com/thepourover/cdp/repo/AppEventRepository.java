package com.thepourover.cdp.repo;

import com.thepourover.cdp.domain.AppEvent;
import com.thepourover.cdp.domain.Subscriber;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppEventRepository extends JpaRepository<AppEvent, Long> {
    Optional<AppEvent> findByEventId(String eventId);

    boolean existsByEventId(String eventId);

    List<AppEvent> findBySubscriberOrderByTimestampAsc(Subscriber subscriber);

    List<AppEvent> findByDeviceIdAndSubscriberIsNull(String deviceId);

    List<AppEvent> findTop50ByOrderByTimestampDesc();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AppEvent e set e.subscriber = :subscriber where e.deviceId = :deviceId and e.subscriber is null")
    int attachDeviceEvents(@Param("deviceId") String deviceId, @Param("subscriber") Subscriber subscriber);

    long countByEventType(String eventType);

    /** Bulk engagement: [subscriberId, eventType, count] for linked app events. */
    @Query("""
            select e.subscriber.id, e.eventType, count(e)
            from AppEvent e
            where e.subscriber is not null
            group by e.subscriber.id, e.eventType
            """)
    List<Object[]> countGroupedBySubscriberAndEventType();
}
