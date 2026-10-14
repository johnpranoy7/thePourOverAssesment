package com.thepourover.cdp.repo;

import com.thepourover.cdp.domain.Subscriber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SubscriberRepository extends JpaRepository<Subscriber, Long> {
    Optional<Subscriber> findByEmail(String email);

    long countByStubFalse();

    @Query("select s.acquisitionSource, count(s) from Subscriber s where s.stub = false group by s.acquisitionSource")
    List<Object[]> countByAcquisitionSource();

    List<Subscriber> findByStubFalse();
}
