package com.jobsuite.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_applications", indexes = {
        @Index(name = "idx_job_applications_user_id", columnList = "user_id"),
        @Index(name = "idx_job_applications_status", columnList = "status"),
        @Index(name = "idx_job_applications_job_id", columnList = "job_id", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobApplication {

    public enum ApplicationStatus {
        DISCOVERED, FILTERING, TAILORING,
        READY, MANUAL_APPLY,
        APPLIED, INTERVIEWING, OFFERED, REJECTED,
        WITHDRAWN, FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "job_id", nullable = false, unique = true)
    private String jobId;

    @Column(name = "job_url", columnDefinition = "TEXT")
    private String jobUrl;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "job_title", nullable = false, length = 500)
    private String jobTitle;

    @Column(name = "job_description", columnDefinition = "TEXT")
    private String jobDescription;

    @Column(name = "job_location")
    private String jobLocation;

    @Column(name = "is_remote")
    private Boolean isRemote;

    @Column(name = "salary_min")
    private Integer salaryMin;

    @Column(name = "salary_max")
    private Integer salaryMax;

    @Column(name = "is_easy_apply")
    private boolean isEasyApply;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
