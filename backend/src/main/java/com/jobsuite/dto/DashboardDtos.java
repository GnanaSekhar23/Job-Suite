package com.jobsuite.dto;

import com.jobsuite.entity.JobApplication.ApplicationStatus;
import com.jobsuite.entity.TailoredResume.TailoringStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

public class DashboardDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobSummaryResponse {
        private Long id;
        private String companyName;
        private String jobTitle;
        private String jobLocation;
        private boolean remote;
        private Integer salaryMin;
        private Integer salaryMax;
        private String jobUrl;
        private ApplicationStatus status;
        private LocalDateTime postedAt;
        private LocalDateTime createdAt;
        private TailoringStatus tailoringStatus;
        private boolean hasResume;
        private boolean hasCoverLetter;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobDetailResponse {
        private Long id;
        private String companyName;
        private String jobTitle;
        private String jobLocation;
        private boolean remote;
        private Integer salaryMin;
        private Integer salaryMax;
        private String jobUrl;
        private ApplicationStatus status;
        private String jobDescription;
        private String notes;
        private LocalDateTime postedAt;
        private LocalDateTime createdAt;
        private String resumePdfUrl;
        private String coverLetterPdfUrl;
        private String resumeLatex;
        private String coverLetterLatex;
        private TailoringStatus tailoringStatus;
        private String tailoringError;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardStatsResponse {
        private long totalDiscovered;
        private long totalReady;
        private long totalFailed;
        private long totalFiltered;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateStatusRequest {
        private ApplicationStatus status;
        private String notes;
    }
}
