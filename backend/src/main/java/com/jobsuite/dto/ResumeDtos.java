package com.jobsuite.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

public class ResumeDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResumeResponse {
        private Long id;
        private String title;
        private String originalPdfUrl;
        @JsonProperty("isActive")
        private boolean isActive;
        private boolean hasLatexTemplate;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResumeListResponse {
        private Long id;
        private String title;
        @JsonProperty("isActive")
        private boolean isActive;
        private boolean hasLatexTemplate;
        private LocalDateTime createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LatexTextRequest {
        private String latex;
    }
}
