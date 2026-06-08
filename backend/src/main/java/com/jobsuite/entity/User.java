package com.jobsuite.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "profile_picture_url")
    private String profilePictureUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AuthProvider provider = AuthProvider.LOCAL;

    @Column(name = "provider_id")
    private String providerId;

    @Column(name = "email_verified")
    @Builder.Default
    private boolean emailVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.USER;

    @Column(name = "daily_apply_limit")
    @Builder.Default
    private int dailyApplyLimit = 50;

    @Column(name = "applies_today")
    @Builder.Default
    private int appliesToday = 0;

    @Column(name = "applies_last_reset")
    private LocalDate appliesLastReset;

    @Column(name = "refresh_token", length = 500)
    private String refreshToken;

    @Column(name = "refresh_token_expiry")
    private LocalDateTime refreshTokenExpiry;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public boolean canApplyToday() {
        if (appliesLastReset == null ||
                !appliesLastReset.equals(LocalDate.now())) {
            appliesToday = 0;
            appliesLastReset = LocalDate.now();
        }
        return appliesToday < dailyApplyLimit;
    }

    public void incrementAppliesToday() {
        appliesToday++;
        appliesLastReset = LocalDate.now();
    }

    public enum AuthProvider {
        LOCAL,
        GOOGLE
    }

    public enum Role {
        USER,
        ADMIN
    }
}