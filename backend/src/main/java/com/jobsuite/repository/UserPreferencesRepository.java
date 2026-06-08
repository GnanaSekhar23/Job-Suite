package com.jobsuite.repository;

import com.jobsuite.entity.User;
import com.jobsuite.entity.UserPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Repository
public interface UserPreferencesRepository extends JpaRepository<UserPreferences, Long> {

    Optional<UserPreferences> findByUser(User user);

    boolean existsByUser(User user);
}
