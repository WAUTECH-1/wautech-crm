package com.wautech.crm.identity.controller;
import com.wautech.crm.identity.dto.SelfProfileRequest;
import com.wautech.crm.identity.dto.UserResponse;
import com.wautech.crm.identity.service.CurrentUserProfileService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/auth/profile")
public class CurrentUserProfileController {
    private final CurrentUserProfileService profiles;
    public CurrentUserProfileController(CurrentUserProfileService profiles) { this.profiles = profiles; }
    @GetMapping public ResponseEntity<UserResponse> get() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(profiles.get());
    }
    @PatchMapping public ResponseEntity<UserResponse> update(@Valid @RequestBody SelfProfileRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(profiles.update(request));
    }
}
