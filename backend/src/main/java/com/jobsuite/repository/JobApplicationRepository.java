package com.jobsuite.repository;

import com.jobsuite.entity.JobApplication;
import com.jobsuite.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.jobsuite.entity.JobApplication.ApplicationStatus;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {
    List<JobApplication> findByUser(User user);

    List<JobApplication> findByUserAndStatus(
            User user,
            ApplicationStatus status
    );

    List<JobApplication> findByStatusAndIsEasyApplyTrue(
            ApplicationStatus status
    );

    boolean existsByJobId(String jobId);
    Optional<JobApplication> findByJobId(String jobId);
    long countByUserAndStatus(User user, ApplicationStatus status);
    List<JobApplication> findByUserAndStatusAndIsEasyApplyTrue(
            User user,
            ApplicationStatus status
    );

}
