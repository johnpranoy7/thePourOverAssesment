package com.thepourover.cdp.web;

import com.thepourover.cdp.service.ProfileService;
import com.thepourover.cdp.web.dto.Dtos.ProfileResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profiles")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ProfileResponse lookup(@RequestParam String email) {
        return profileService.findByEmail(email);
    }
}
