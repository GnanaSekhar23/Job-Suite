package com.jobsuite.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_preferences")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "desired_job_title")
    private String desiredJobTitle;

    @Column(name = "expanded_job_titles", columnDefinition = "TEXT")
    private String expandedJobTitles;

    @Column(columnDefinition = "TEXT")
    private String skills;

    @Column(name = "country", length = 10)
    @Builder.Default
    private String country = "us";

    @Column(name = "desired_salary_min")
    private Integer desiredSalaryMin;

    @Column(name = "desired_salary_max")
    private Integer desiredSalaryMax;

    @Column(name = "remote_only")
    @Builder.Default
    private boolean remoteOnly = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "experience_level", length = 20)
    @Builder.Default
    private ExperienceLevel experienceLevel = ExperienceLevel.MID;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "years_of_experience")
    private Integer yearsOfExperience;

    @Column(name = "requires_sponsorship")
    @Builder.Default
    private boolean requiresSponsorship = false;

    @Column(name = "willing_to_relocate")
    @Builder.Default
    private boolean willingToRelocate = false;

    @Column(name = "linkedin_url")
    private String linkedinUrl;
    @Column(name = "github_url")
    private String githubUrl;

    @Column(name = "portfolio_url")
    private String portfolioUrl;



    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum ExperienceLevel{
        ENTRY,
        MID,
        SENIOR
    }

}
