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

    @Modifying
    @Query("update WebEvent w set w.subscriber = :subscriber where w.visitorId = :visitorId")
    int linkVisitorToSubscriber(@Param("visitorId") String visitorId, @Param("subscriber") Subscriber subscriber);
}
