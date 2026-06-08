package com.jobsuite.service;

import com.jobsuite.dto.PreferencesDtos.*;
import com.jobsuite.entity.User;
import com.jobsuite.entity.UserPreferences;
import com.jobsuite.exception.AppException;
import com.jobsuite.repository.UserPreferencesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PreferencesService {

    private final UserPreferencesRepository preferencesRepository;
    private final ApifyService apifyService;

    @Transactional
    public PreferencesResponse savePreferences(User user, PreferencesRequest request) {
        UserPreferences preferences = preferencesRepository.findByUser(user)
                .orElse(UserPreferences.builder().user(user).build());

        boolean titleChanged = !request.getDesiredJobTitle().equals(preferences.getDesiredJobTitle());

        preferences.setDesiredJobTitle(request.getDesiredJobTitle());
        preferences.setCountry(request.getCountry());
        preferences.setDesiredSalaryMin(request.getDesiredSalaryMin());
        preferences.setDesiredSalaryMax(request.getDesiredSalaryMax());
        preferences.setRemoteOnly(request.isRemoteOnly());
        preferences.setExperienceLevel(request.getExperienceLevel());
        preferences.setSkills(request.getSkills());
        preferences.setPhoneNumber(request.getPhoneNumber());
        preferences.setYearsOfExperience(request.getYearsOfExperience());
        preferences.setRequiresSponsorship(request.isRequiresSponsorship());
        preferences.setWillingToRelocate(request.isWillingToRelocate());
        preferences.setLinkedinUrl(request.getLinkedinUrl());
        preferences.setGithubUrl(request.getGithubUrl());
        preferences.setPortfolioUrl(request.getPortfolioUrl());

        if (titleChanged) preferences.setExpandedJobTitles(null);

        preferences = preferencesRepository.save(preferences);

        if (titleChanged || preferences.getExpandedJobTitles() == null) {
            final UserPreferences finalPrefs = preferences;
            new Thread(() -> apifyService.expandAndSaveJobTitles(finalPrefs)).start();
        }

        log.info("Preferences saved for: {}", user.getEmail());
        return mapToResponse(preferences);
    }

    public PreferencesResponse getPreferences(User user) {
        UserPreferences preferences = preferencesRepository.findByUser(user)
                .orElseThrow(() -> new AppException("Preferences not found.", HttpStatus.NOT_FOUND));
        return mapToResponse(preferences);
    }

    private PreferencesResponse mapToResponse(UserPreferences p) {
        return PreferencesResponse.builder()
                .id(p.getId())
                .desiredJobTitle(p.getDesiredJobTitle())
                .expandedJobTitles(p.getExpandedJobTitles())
                .country(p.getCountry())
                .desiredSalaryMin(p.getDesiredSalaryMin())
                .desiredSalaryMax(p.getDesiredSalaryMax())
                .remoteOnly(p.isRemoteOnly())
                .experienceLevel(p.getExperienceLevel())
                .skills(p.getSkills())
                .phoneNumber(p.getPhoneNumber())
                .yearsOfExperience(p.getYearsOfExperience())
                .requiresSponsorship(p.isRequiresSponsorship())
                .willingToRelocate(p.isWillingToRelocate())
                .linkedinUrl(p.getLinkedinUrl())
                .githubUrl(p.getGithubUrl())
                .portfolioUrl(p.getPortfolioUrl())
                .build();
    }
}
