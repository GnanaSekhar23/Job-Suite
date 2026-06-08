package com.jobsuite.service;

import com.jobsuite.dto.AuthDtos.*;
import com.jobsuite.entity.User;
import com.jobsuite.entity.UserPreferences;
import com.jobsuite.exception.AppException;
import com.jobsuite.repository.UserPreferencesRepository;
import com.jobsuite.repository.UserRepository;
import com.jobsuite.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;




@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserPreferencesRepository userPreferencesRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(
                request.getEmail().toLowerCase())) {
            throw new AppException(
                    "An account with this email already exists",
                    HttpStatus.CONFLICT
            );
        }

        User user = User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(
                        request.getPassword()))
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .provider(User.AuthProvider.LOCAL)
                .role(User.Role.USER)
                .emailVerified(true)
                .build();

        user = userRepository.save(user);
        log.info("New user registered: {}", user.getEmail());

        UserPreferences preferences = UserPreferences.builder()
                .user(user)
                .build();
        userPreferencesRepository.save(preferences);

        return generateAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail().toLowerCase(),
                            request.getPassword()
                    )
            );
        } catch (BadCredentialsException e) {
            throw new AppException(
                    "Invalid email or password",
                    HttpStatus.UNAUTHORIZED
            );
        }

        User user = userRepository.findByEmail(
                        request.getEmail().toLowerCase())
                .orElseThrow(() -> new AppException(
                        "User not found",
                        HttpStatus.NOT_FOUND
                ));

        log.info("User logged in: {}", user.getEmail());
        return generateAuthResponse(user);
    }

    @Transactional
    public AuthResponse refreshToken(
            RefreshTokenRequest request) {

        User user = userRepository
                .findByRefreshToken(request.getRefreshToken())
                .orElseThrow(() -> new AppException(
                        "Invalid refresh token",
                        HttpStatus.UNAUTHORIZED
                ));

        if (user.getRefreshTokenExpiry() == null ||
                user.getRefreshTokenExpiry()
                        .isBefore(LocalDateTime.now())) {
            throw new AppException(
                    "Refresh token has expired. Please log in again.",
                    HttpStatus.UNAUTHORIZED
            );
        }

        if (!jwtService.isTokenValid(
                request.getRefreshToken(), user.getId())) {
            throw new AppException(
                    "Invalid refresh token",
                    HttpStatus.UNAUTHORIZED
            );
        }

        log.debug("Token refreshed for user: {}",
                user.getEmail());
        return generateAuthResponse(user);
    }

    @Transactional
    public void logout(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(
                        "User not found",
                        HttpStatus.NOT_FOUND
                ));

        user.setRefreshToken(null);
        user.setRefreshTokenExpiry(null);
        userRepository.save(user);

        log.info("User logged out: {}", user.getEmail());
    }

    public UserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(
                        "User not found",
                        HttpStatus.NOT_FOUND
                ));
        return mapToUserResponse(user);
    }

    private AuthResponse generateAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(
                user.getId(), user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(
                user.getId(), user.getEmail());

        user.setRefreshToken(refreshToken);
        user.setRefreshTokenExpiry(
                LocalDateTime.now().plusDays(7)
        );
        userRepository.save(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(mapToUserResponse(user))
                .build();
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .profilePictureUrl(user.getProfilePictureUrl())
                .role(user.getRole().name())
                .provider(user.getProvider().name())
                .emailVerified(user.isEmailVerified())
                .build();
    }

    @Transactional
    public AuthResponse googleLogin(String idToken) {
        // Verify token with Google
        GoogleUserInfo googleUser = verifyGoogleToken(idToken);

        // Find or create user
        User user = userRepository.findByEmail(googleUser.email().toLowerCase())
                .orElse(null);

        if (user == null) {
            // New user — create account
            user = User.builder()
                    .email(googleUser.email().toLowerCase())
                    .firstName(googleUser.firstName())
                    .lastName(googleUser.lastName())
                    .profilePictureUrl(googleUser.picture())
                    .provider(User.AuthProvider.GOOGLE)
                    .providerId(googleUser.sub())
                    .role(User.Role.USER)
                    .emailVerified(true)
                    .passwordHash(passwordEncoder.encode(
                            java.util.UUID.randomUUID().toString()))
                    .build();
            user = userRepository.save(user);

            // Create default preferences
            UserPreferences preferences = UserPreferences.builder().user(user).build();
            userPreferencesRepository.save(preferences);

            log.info("New Google user registered: {}", user.getEmail());
        } else {
            // Existing user — update profile pic if changed
            if (googleUser.picture() != null) {
                user.setProfilePictureUrl(googleUser.picture());
                user.setProvider(User.AuthProvider.GOOGLE);
                userRepository.save(user);
            }
            log.info("Google user logged in: {}", user.getEmail());
        }

        return generateAuthResponse(user);
    }

    private GoogleUserInfo verifyGoogleToken(String idToken) {
        try {
            String url = "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken;
            RestTemplate restTemplate = new RestTemplate();
            com.fasterxml.jackson.databind.JsonNode response =
                    restTemplate.getForObject(url, com.fasterxml.jackson.databind.JsonNode.class);

            if (response == null || response.has("error")) {
                throw new AppException("Invalid Google token", HttpStatus.UNAUTHORIZED);
            }

            String email = response.path("email").asText();
            String sub = response.path("sub").asText();
            String givenName = response.path("given_name").asText("");
            String familyName = response.path("family_name").asText("");
            String picture = response.path("picture").asText("");
            String name = response.path("name").asText("");

            // If no given/family name, split full name
            if (givenName.isBlank() && !name.isBlank()) {
                String[] parts = name.split(" ", 2);
                givenName = parts[0];
                familyName = parts.length > 1 ? parts[1] : "";
            }

            if (email == null || email.isBlank()) {
                throw new AppException("Could not get email from Google", HttpStatus.UNAUTHORIZED);
            }

            return new GoogleUserInfo(email, sub, givenName, familyName, picture);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google token verification failed: {}", e.getMessage());
            throw new AppException("Google authentication failed", HttpStatus.UNAUTHORIZED);
        }
    }

    private record GoogleUserInfo(
            String email,
            String sub,
            String firstName,
            String lastName,
            String picture) {}

}