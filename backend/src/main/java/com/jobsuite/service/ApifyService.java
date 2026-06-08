package com.jobsuite.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobsuite.config.AppConfig;
import com.jobsuite.entity.JobApplication;
import com.jobsuite.entity.User;
import com.jobsuite.entity.UserPreferences;
import com.jobsuite.repository.JobApplicationRepository;
import com.jobsuite.repository.UserPreferencesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApifyService {

    private final AppConfig appConfig;
    private final JobApplicationRepository jobApplicationRepository;
    private final UserPreferencesRepository preferencesRepository;
    private final AiService aiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();


    public int fetchJobsForUser(User user) {
        UserPreferences prefs = preferencesRepository.findByUser(user).orElse(null);
        if (prefs == null || prefs.getDesiredJobTitle() == null) {
            log.warn("No preferences for user: {}", user.getEmail());
            return 0;
        }

        List<String> titles = getTitlesToSearch(prefs);
        int totalNewJobs = 0;

        for (String title : titles) {
            try {
                int count = fetchAndSaveJobs(user, prefs, title);
                totalNewJobs += count;
                Thread.sleep(1000);
            } catch (Exception e) {
                log.error("Error fetching jobs for '{}': {}", title, e.getMessage());
            }
        }

        log.info("Fetched {} new jobs for: {}", totalNewJobs, user.getEmail());
        return totalNewJobs;
    }

    private List<String> getTitlesToSearch(UserPreferences prefs) {
        List<String> titles = new ArrayList<>();
        if (prefs.getExpandedJobTitles() != null && !prefs.getExpandedJobTitles().isBlank()) {
            for (String t : prefs.getExpandedJobTitles().split(",")) {
                String trimmed = t.trim();
                if (!trimmed.isEmpty()) titles.add(trimmed);
            }
        }
        if (!titles.contains(prefs.getDesiredJobTitle())) {
            titles.add(0, prefs.getDesiredJobTitle());
        }
        if (titles.size() > 4) titles = titles.subList(0, 4);
        return titles;
    }

    private int fetchAndSaveJobs(User user, UserPreferences prefs, String jobTitle)
            throws IOException, InterruptedException {

        String query = buildQuery(jobTitle, prefs);
        String country = prefs.getCountry() != null ? prefs.getCountry().toUpperCase() : "US";
        String apifyToken = appConfig.getApify().getApiToken();

        // Build input for misceres~indeed-scraper
        ObjectNode input = objectMapper.createObjectNode();
        input.put("position", query);
        input.put("country", country);
        input.put("location", "");
        input.put("maxItems", 15);
        input.put("parseCompanyDetails", false);
        input.put("saveOnlyUniqueItems", true);
        input.put("followApplyRedirects", false);
        input.put("fromAge", 1);

        String runUrl = "https://api.apify.com/v2/acts/misceres~indeed-scraper/run-sync-get-dataset-items"
                + "?token=" + apifyToken
                + "&timeout=120&memory=256";

        RequestBody body = RequestBody.create(
                objectMapper.writeValueAsString(input),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(runUrl)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();

        log.info("Calling Apify for: {} in {}", query, country);

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "no body";
                log.error("Apify error {}: {}", response.code(), err);
                return 0;
            }

            String responseBody = response.body().string();
            JsonNode items = objectMapper.readTree(responseBody);

            if (!items.isArray() || items.size() == 0) {
                log.warn("Apify returned no items for: {}", query);
                return 0;
            }

            // Log first item fields so we know the exact field names
            JsonNode first = items.get(0);
            log.info("=== APIFY FIELD NAMES (first item) ===");
            Iterator<Map.Entry<String, JsonNode>> fields = first.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                log.info("  Field: {} = {}", field.getKey(),
                        field.getValue().asText("").substring(0,
                                Math.min(80, field.getValue().asText("").length())));
            }
            log.info("=== END APIFY FIELDS ===");

            int saved = 0;
            for (JsonNode item : items) {
                try {
                    if (processApifyJob(user, item)) saved++;
                } catch (Exception e) {
                    log.warn("Error processing job: {}", e.getMessage());
                }
            }
            log.info("Saved {}/{} jobs for: {}", saved, items.size(), query);
            return saved;
        }
    }

    @Transactional
    public boolean processApifyJob(User user, JsonNode item) {
        // Try all possible ID field names
        String jobId = getFirstNonEmpty(item,
                "id", "jobId", "job_id", "positionId", "scrapedAt");

        // Use URL as fallback ID
        if (jobId == null || jobId.isBlank()) {
            String url = getFirstNonEmpty(item, "url", "jobUrl", "applyUrl", "externalApplyUrl");
            String title = getFirstNonEmpty(item, "title", "positionName", "jobTitle");
            if (url != null && title != null) {
                jobId = (title + url).replaceAll("[^a-zA-Z0-9]", "").substring(0,
                        Math.min(100, (title + url).replaceAll("[^a-zA-Z0-9]", "").length()));
            }
        }

        if (jobId == null || jobId.isBlank()) {
            log.debug("Skipping job with no ID");
            return false;
        }

        if (jobApplicationRepository.existsByJobId(jobId)) {
            log.debug("Skipping duplicate: {}", jobId);
            return false;
        }

        // Try all possible field names for each field
        String title = getFirstNonEmpty(item, "title", "positionName", "jobTitle", "position");
        String company = getFirstNonEmpty(item, "company", "companyName", "employer", "employerName");
        String description = getFirstNonEmpty(item, "description", "jobDescription", "snippet", "summary");
        String applyUrl = getFirstNonEmpty(item, "externalApplyUrl", "applyUrl", "jobUrl", "url", "link");
        String location = getFirstNonEmpty(item, "location", "jobLocation", "city", "formattedLocation");

        if (title == null || title.isBlank()) {
            log.debug("Skipping job with no title");
            return false;
        }
        if (applyUrl == null || applyUrl.isBlank()) {
            log.debug("Skipping job '{}' with no apply URL", title);
            return false;
        }
        if (description == null || description.isBlank()) {
            log.debug("Skipping job '{}' with no description", title);
            return false;
        }

        boolean isRemote = (location != null && location.toLowerCase().contains("remote"))
                || item.path("remote").asBoolean(false)
                || item.path("isRemote").asBoolean(false);

        boolean isEasyApply = applyUrl.contains("indeed.com") || applyUrl.contains("linkedin.com");

        // Salary
        Integer salaryMin = null;
        Integer salaryMax = null;
        String salaryStr = getFirstNonEmpty(item, "salary", "salaryRange", "compensation");
        if (salaryStr != null) {
            salaryMin = parseSalaryMin(salaryStr);
            salaryMax = parseSalaryMax(salaryStr);
        }

        JobApplication job = JobApplication.builder()
                .user(user)
                .jobId(jobId)
                .jobUrl(applyUrl)
                .companyName(company != null ? company : "Unknown")
                .jobTitle(title)
                .jobDescription(description.length() > 8000 ? description.substring(0, 8000) : description)
                .jobLocation(location != null ? location : "")
                .isRemote(isRemote)
                .salaryMin(salaryMin)
                .salaryMax(salaryMax)
                .postedAt(LocalDateTime.now())
                .isEasyApply(isEasyApply)
                .status(JobApplication.ApplicationStatus.DISCOVERED)
                .build();

        jobApplicationRepository.save(job);
        log.info("Saved: {} at {}", title, company);
        return true;
    }

    // Helper — returns first non-empty value from any of the given field names
    private String getFirstNonEmpty(JsonNode node, String... fieldNames) {
        for (String field : fieldNames) {
            JsonNode val = node.path(field);
            if (!val.isMissingNode() && !val.isNull()) {
                String text = val.asText("").trim();
                if (!text.isEmpty() && !text.equals("null")) {
                    return text;
                }
            }
        }
        return null;
    }

    private String buildQuery(String jobTitle, UserPreferences prefs) {
        StringBuilder q = new StringBuilder();
        if (prefs.getExperienceLevel() != null) {
            switch (prefs.getExperienceLevel()) {
                case ENTRY -> q.append("entry level ");
                case SENIOR -> q.append("senior ");
                default -> {}
            }
        }
        q.append(jobTitle);
        return q.toString().trim();
    }

    private Integer parseSalaryMin(String salary) {
        if (salary == null || salary.isBlank()) return null;
        try {
            String cleaned = salary.replaceAll("[^0-9\\s]", " ").trim();
            String[] parts = cleaned.trim().split("\\s+");
            if (parts.length > 0 && !parts[0].isBlank()) {
                int val = Integer.parseInt(parts[0].trim());
                return val < 500 ? val * 2080 : val;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Integer parseSalaryMax(String salary) {
        if (salary == null || salary.isBlank()) return null;
        try {
            String cleaned = salary.replaceAll("[^0-9\\s]", " ").trim();
            String[] parts = cleaned.trim().split("\\s+");
            if (parts.length > 1) {
                int val = Integer.parseInt(parts[parts.length - 1].trim());
                return val < 500 ? val * 2080 : val;
            }
        } catch (Exception ignored) {}
        return null;
    }

    public void expandAndSaveJobTitles(UserPreferences prefs) {
        try {
            String expanded = aiService.expandJobTitles(
                    prefs.getDesiredJobTitle(),
                    prefs.getSkills(),
                    prefs.getExperienceLevel() != null ? prefs.getExperienceLevel().name() : "MID"
            );
            prefs.setExpandedJobTitles(expanded);
            preferencesRepository.save(prefs);
            log.info("Expanded job titles: {}", expanded);
        } catch (Exception e) {
            log.warn("Could not expand job titles: {}", e.getMessage());
        }
    }
}
