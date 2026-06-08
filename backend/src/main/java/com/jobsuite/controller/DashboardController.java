package com.jobsuite.controller;

import com.jobsuite.dto.DashboardDtos.*;
import com.jobsuite.entity.JobApplication;
import com.jobsuite.entity.JobApplication.ApplicationStatus;
import com.jobsuite.entity.User;
import com.jobsuite.repository.JobApplicationRepository;
import com.jobsuite.service.DashboardService;
import com.jobsuite.service.TailoringService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final TailoringService tailoringService;
    private final JobApplicationRepository jobApplicationRepository;

    @GetMapping("/jobs")
    public ResponseEntity<List<JobSummaryResponse>> getAllJobs(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(dashboardService.getAllJobs(user));
    }

    @GetMapping("/jobs/status/{status}")
    public ResponseEntity<List<JobSummaryResponse>> getJobsByStatus(
            @AuthenticationPrincipal User user, @PathVariable ApplicationStatus status) {
        return ResponseEntity.ok(dashboardService.getJobsByStatus(user, status));
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<JobDetailResponse> getJobDetail(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(dashboardService.getJobDetail(user, id));
    }

    @PatchMapping("/jobs/{id}/status")
    public ResponseEntity<JobSummaryResponse> updateStatus(
            @AuthenticationPrincipal User user, @PathVariable Long id,
            @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(dashboardService.updateJobStatus(user, id, request));
    }

    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsResponse> getStats(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(dashboardService.getStats(user));
    }

    @PostMapping("/tailor-from-extension")
    public ResponseEntity<String> tailorFromExtension(
            @AuthenticationPrincipal User user,
            @RequestBody ExtensionJobRequest request) {

        String jobId = request.getJobUrl() != null ? request.getJobUrl() : String.valueOf(System.currentTimeMillis());

        if (jobApplicationRepository.existsByJobId(jobId)) {
            return ResponseEntity.ok("Job already in dashboard — check there for status.");
        }

        JobApplication job = JobApplication.builder()
                .user(user)
                .jobId(jobId)
                .jobUrl(request.getJobUrl())
                .companyName(request.getCompanyName() != null ? request.getCompanyName() : "Unknown")
                .jobTitle(request.getJobTitle() != null ? request.getJobTitle() : "Unknown Position")
                .jobDescription(request.getJobDescription() != null ? request.getJobDescription() : "")
                .jobLocation(request.getJobLocation())
                .isRemote(request.isRemote())
                .isEasyApply(request.isEasyApply())
                .postedAt(LocalDateTime.now())
                .status(ApplicationStatus.DISCOVERED)
                .build();

        jobApplicationRepository.save(job);
        tailoringService.tailorJob(job);

        return ResponseEntity.ok("Job saved! Resume tailoring started. Check dashboard in 2-3 minutes.");
    }


    @DeleteMapping("/jobs/{id}")
    public ResponseEntity<Void> deleteJob(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        dashboardService.deleteJob(user, id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/jobs/date/{dateStr}")
    public ResponseEntity<Void> deleteJobsByDate(
            @AuthenticationPrincipal User user,
            @PathVariable String dateStr) {
        dashboardService.deleteJobsByDate(user, dateStr);
        return ResponseEntity.ok().build();
    }

    @Data
    public static class ExtensionJobRequest {
        private String jobTitle;
        private String companyName;
        private String jobDescription;
        private String jobUrl;
        private String jobLocation;
        private boolean isRemote;
        private boolean isEasyApply;
    }
}
