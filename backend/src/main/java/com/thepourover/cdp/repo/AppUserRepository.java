package com.thepourover.cdp.repo;

import com.thepourover.cdp.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppUserRepository extends JpaRepository<AppUser, String> {
    List<AppUser> findBySubscriberId(Long subscriberId);
}
