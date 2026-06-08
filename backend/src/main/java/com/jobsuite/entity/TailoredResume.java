package com.jobsuite.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "tailored_resumes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TailoredResume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_application_id", nullable = false)
    private JobApplication jobApplication;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_resume_id", nullable = false)
    private BaseResume baseResume;

    @Column(name = "resume_latex", columnDefinition = "TEXT")
    private String resumeLatex;

    @Column(name = "cover_letter_latex", columnDefinition = "TEXT")
    private String coverLetterLatex;

    @Column(name = "resume_pdf_key")
    private String resumePdfKey;

    @Column(name = "cover_letter_pdf_key")
    private String coverLetterPdfKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TailoringStatus status = TailoringStatus.PENDING;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum TailoringStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}