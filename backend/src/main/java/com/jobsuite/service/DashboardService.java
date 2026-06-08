package com.jobsuite.service;

import com.jobsuite.dto.DashboardDtos.*;
import com.jobsuite.entity.JobApplication;
import com.jobsuite.entity.JobApplication.ApplicationStatus;
import com.jobsuite.entity.TailoredResume;
import com.jobsuite.entity.User;
import com.jobsuite.exception.AppException;
import com.jobsuite.repository.JobApplicationRepository;
import com.jobsuite.repository.TailoredResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DashboardService {

    private final JobApplicationRepository jobApplicationRepository;
    private final TailoredResumeRepository tailoredResumeRepository;
    private final StorageService storageService;

    public List<JobSummaryResponse> getAllJobs(User user) {
        return jobApplicationRepository.findByUser(user)
                .stream()
                .filter(job -> job.getStatus() != ApplicationStatus.WITHDRAWN)
                .map(this::mapToSummary)
                .collect(Collectors.toList());
    }

    public List<JobSummaryResponse> getJobsByStatus(User user, ApplicationStatus status) {
        return jobApplicationRepository.findByUserAndStatus(user, status)
                .stream()
                .map(this::mapToSummary)
                .collect(Collectors.toList());
    }

    public JobDetailResponse getJobDetail(User user, Long jobId) {
        JobApplication job = jobApplicationRepository.findById(jobId)
                .orElseThrow(() -> new AppException("Job not found", HttpStatus.NOT_FOUND));

        if (!job.getUser().getId().equals(user.getId())) {
            throw new AppException("Access denied", HttpStatus.FORBIDDEN);
        }

        TailoredResume tailored = tailoredResumeRepository
                .findByJobApplication(job).orElse(null);

        String resumeUrl = null;
        String coverLetterUrl = null;

        if (tailored != null) {
            if (tailored.getResumePdfKey() != null) {
                resumeUrl = storageService.getFileUrl(tailored.getResumePdfKey());
            }
            if (tailored.getCoverLetterPdfKey() != null) {
                coverLetterUrl = storageService.getFileUrl(tailored.getCoverLetterPdfKey());
            }
        }

        return JobDetailResponse.builder()
                .id(job.getId())
                .companyName(job.getCompanyName())
                .jobTitle(job.getJobTitle())
                .jobLocation(job.getJobLocation())
                .remote(job.getIsRemote() != null && job.getIsRemote())
                .salaryMin(job.getSalaryMin())
                .salaryMax(job.getSalaryMax())
                .jobUrl(job.getJobUrl())
                .status(job.getStatus())
                .jobDescription(job.getJobDescription())
                .notes(job.getNotes())
                .postedAt(job.getPostedAt())
                .createdAt(job.getCreatedAt())
                .resumePdfUrl(resumeUrl)
                .coverLetterPdfUrl(coverLetterUrl)
                .resumeLatex(tailored != null ? tailored.getResumeLatex() : null)
                .coverLetterLatex(tailored != null ? tailored.getCoverLetterLatex() : null)
                .tailoringStatus(tailored != null ? tailored.getStatus() : null)
                .tailoringError(tailored != null ? tailored.getErrorMessage() : null)
                .build();
    }

    @Transactional
    public JobSummaryResponse updateJobStatus(User user, Long jobId, UpdateStatusRequest request) {
        JobApplication job = jobApplicationRepository.findById(jobId)
                .orElseThrow(() -> new AppException("Job not found", HttpStatus.NOT_FOUND));

        if (!job.getUser().getId().equals(user.getId())) {
            throw new AppException("Access denied", HttpStatus.FORBIDDEN);
        }

        job.setStatus(request.getStatus());
        if (request.getNotes() != null) job.setNotes(request.getNotes());
        job = jobApplicationRepository.save(job);
        return mapToSummary(job);
    }

    public DashboardStatsResponse getStats(User user) {
        List<JobApplication> allJobs = jobApplicationRepository.findByUser(user);
        return DashboardStatsResponse.builder()
                .totalDiscovered(allJobs.stream()
                        .filter(j -> j.getStatus() == ApplicationStatus.DISCOVERED
                                || j.getStatus() == ApplicationStatus.FILTERING
                                || j.getStatus() == ApplicationStatus.TAILORING)
                        .count())
                .totalReady(allJobs.stream()
                        .filter(j -> j.getStatus() == ApplicationStatus.READY
                                || j.getStatus() == ApplicationStatus.MANUAL_APPLY)
                        .count())
                .totalFailed(allJobs.stream()
                        .filter(j -> j.getStatus() == ApplicationStatus.FAILED)
                        .count())
                .totalFiltered(allJobs.stream()
                        .filter(j -> j.getStatus() == ApplicationStatus.WITHDRAWN)
                        .count())
                .build();
    }

    private JobSummaryResponse mapToSummary(JobApplication job) {
        TailoredResume tailored = tailoredResumeRepository
                .findByJobApplication(job).orElse(null);
        return JobSummaryResponse.builder()
                .id(job.getId())
                .companyName(job.getCompanyName())
                .jobTitle(job.getJobTitle())
                .jobLocation(job.getJobLocation())
                .remote(job.getIsRemote() != null && job.getIsRemote())
                .salaryMin(job.getSalaryMin())
                .salaryMax(job.getSalaryMax())
                .jobUrl(job.getJobUrl())
                .status(job.getStatus())
                .postedAt(job.getPostedAt())
                .createdAt(job.getCreatedAt())
                .tailoringStatus(tailored != null ? tailored.getStatus() : null)
                .hasResume(tailored != null && tailored.getResumePdfKey() != null)
                .hasCoverLetter(tailored != null && tailored.getCoverLetterPdfKey() != null)
                .build();
    }

    @Transactional
    public void deleteJob(User user, Long jobId) {
        JobApplication job = jobApplicationRepository.findById(jobId)
                .orElseThrow(() -> new AppException("Job not found", HttpStatus.NOT_FOUND));

        if (!job.getUser().getId().equals(user.getId())) {
            throw new AppException("Access denied", HttpStatus.FORBIDDEN);
        }

        // Delete Cloudinary files first
        tailoredResumeRepository.findByJobApplication(job).ifPresent(tailored -> {
            if (tailored.getResumePdfKey() != null) {
                storageService.deleteFile(tailored.getResumePdfKey());
            }
            if (tailored.getCoverLetterPdfKey() != null) {
                storageService.deleteFile(tailored.getCoverLetterPdfKey());
            }
            tailoredResumeRepository.delete(tailored);
        });

        jobApplicationRepository.delete(job);
        log.info("Deleted job {} and Cloudinary files for user {}", jobId, user.getEmail());
    }

    @Transactional
    public void deleteJobsByDate(User user, String dateStr) {
        List<JobApplication> allJobs = jobApplicationRepository.findByUser(user);

        List<JobApplication> toDelete = allJobs.stream()
                .filter(job -> {
                    if (job.getCreatedAt() == null) return false;
                    String jobDate = job.getCreatedAt().toLocalDate()
                            .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy"));
                    return jobDate.equals(dateStr);
                })
                .toList();

        int deleted = 0;
        for (JobApplication job : toDelete) {
            // Delete Cloudinary files
            tailoredResumeRepository.findByJobApplication(job).ifPresent(tailored -> {
                if (tailored.getResumePdfKey() != null) {
                    storageService.deleteFile(tailored.getResumePdfKey());
                }
                if (tailored.getCoverLetterPdfKey() != null) {
                    storageService.deleteFile(tailored.getCoverLetterPdfKey());
                }
                tailoredResumeRepository.delete(tailored);
            });
            jobApplicationRepository.delete(job);
            deleted++;
        }

        log.info("Deleted {} jobs + Cloudinary files from date {} for user {}",
                deleted, dateStr, user.getEmail());
    }
}
