package com.jobsuite.dto;

import com.jobsuite.entity.UserPreferences.ExperienceLevel;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class PreferencesDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreferencesRequest {

        @NotBlank(message = "Job title is required")
        private String desiredJobTitle;
        // e.g. "Software Engineer"

        @NotBlank(message = "Location is required")
        private String country;


        private Integer desiredSalaryMin;
        private Integer desiredSalaryMax;
        // Optional — not everyone wants to filter by salary

        private boolean remoteOnly;

        private ExperienceLevel experienceLevel;
        // ENTRY, MID, or SENIOR

        private String skills;
        // e.g. "Java, Spring Boot, React, PostgreSQL"

        // These are used to auto-answer screening questions
        private String phoneNumber;
        private Integer yearsOfExperience;
        private boolean requiresSponsorship;
        private boolean willingToRelocate;
        private String linkedinUrl;
        private String githubUrl;
        private String portfolioUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreferencesResponse {
        private Long id;
        private String desiredJobTitle;
        private String expandedJobTitles;
        // Claude-generated related titles
        // shown so user knows what we're searching for
        private String country;
        private Integer desiredSalaryMin;
        private Integer desiredSalaryMax;
        private boolean remoteOnly;
        private ExperienceLevel experienceLevel;
        private String skills;
        private String phoneNumber;
        private Integer yearsOfExperience;
        private boolean requiresSponsorship;
        private boolean willingToRelocate;
        private String linkedinUrl;
        private String githubUrl;
        private String portfolioUrl;
    }
}