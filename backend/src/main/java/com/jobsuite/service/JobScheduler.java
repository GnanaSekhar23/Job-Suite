package com.jobsuite.service;

import com.jobsuite.entity.JobApplication.ApplicationStatus;
import com.jobsuite.entity.User;
import com.jobsuite.repository.JobApplicationRepository;
import com.jobsuite.repository.TailoredResumeRepository;
import com.jobsuite.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobScheduler {

    private final UserRepository userRepository;
    private final ApifyService apifyService;
    private final TailoringService tailoringService;
    private final JobApplicationRepository jobApplicationRepository;
    private final TailoredResumeRepository tailoredResumeRepository;

    // Run every day at 8 AM — fetch and tailor new jobs
    @Scheduled(cron = "0 0 8 * * *")
    public void dailyJobFetch() {
        log.info("=== Daily job fetch starting at 8 AM ===");
        List<User> users = userRepository.findAll();
        for (User user : users) {
            try {
                int count = apifyService.fetchJobsForUser(user);
                log.info("Fetched {} new jobs for: {}", count, user.getEmail());
                if (count > 0) {
                    tailoringService.processDiscoveredJobs(user);
                }
            } catch (Exception e) {
                log.error("Daily fetch failed for {}: {}", user.getEmail(), e.getMessage());
            }
        }
    }

    // Run every day at midnight — delete previous day's FAILED/WITHDRAWN jobs
    // and reset daily apply counts
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void midnightCleanup() {
        log.info("=== Midnight cleanup starting ===");

        // Delete jobs older than 24 hours that are FAILED or WITHDRAWN
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);

        List<com.jobsuite.entity.JobApplication> oldFailedJobs = jobApplicationRepository
                .findAll()
                .stream()
                .filter(j -> (j.getStatus() == ApplicationStatus.FAILED ||
                              j.getStatus() == ApplicationStatus.WITHDRAWN) &&
                             j.getCreatedAt() != null &&
                             j.getCreatedAt().isBefore(cutoff))
                .toList();

        for (com.jobsuite.entity.JobApplication job : oldFailedJobs) {
            // Delete associated tailored resumes first
            tailoredResumeRepository.findByJobApplication(job)
                    .ifPresent(tailoredResumeRepository::delete);
            jobApplicationRepository.delete(job);
        }

        log.info("Deleted {} old failed/withdrawn jobs", oldFailedJobs.size());

        // Reset daily apply counts
        List<User> users = userRepository.findAll();
        for (User user : users) {
            user.setAppliesToday(0);
            userRepository.save(user);
        }
        log.info("Reset daily apply counts for {} users", users.size());
    }
}
