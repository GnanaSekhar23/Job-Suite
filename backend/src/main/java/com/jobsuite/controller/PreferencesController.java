package com.jobsuite.controller;

import com.jobsuite.dto.PreferencesDtos.*;
import com.jobsuite.entity.User;
import com.jobsuite.service.ApifyService;
import com.jobsuite.service.PreferencesService;
import com.jobsuite.service.TailoringService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/preferences")
@RequiredArgsConstructor
public class PreferencesController {

    private final PreferencesService preferencesService;
    private final ApifyService apifyService;
    private final TailoringService tailoringService;

    @PostMapping
    public ResponseEntity<PreferencesResponse> savePreferences(
            @AuthenticationPrincipal User user,
            @RequestBody @Valid PreferencesRequest request) {
        return ResponseEntity.ok(preferencesService.savePreferences(user, request));
    }

    @GetMapping
    public ResponseEntity<PreferencesResponse> getPreferences(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(preferencesService.getPreferences(user));
    }

    @PostMapping("/fetch-jobs")
    public ResponseEntity<String> fetchJobs(@AuthenticationPrincipal User user) {
        int count = apifyService.fetchJobsForUser(user);
        return ResponseEntity.ok("Fetched " + count + " new jobs!");
    }

    @PostMapping("/tailor-jobs")
    public ResponseEntity<String> tailorJobs(@AuthenticationPrincipal User user) {
        tailoringService.processDiscoveredJobs(user);
        return ResponseEntity.ok("Tailoring started in background! Check dashboard for progress.");
    }
}
