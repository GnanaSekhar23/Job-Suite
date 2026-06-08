package com.jobsuite.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "base_resumes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaseResume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "original_pdf_key")
    private String originalPdfKey;

    @Column(name = "parsed_content", columnDefinition = "TEXT")
    private String parsedContent;

    // Optional LaTeX template uploaded by user
    @Column(name = "latex_template", columnDefinition = "TEXT")
    private String latexTemplate;

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
