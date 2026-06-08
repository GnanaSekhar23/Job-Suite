package com.jobsuite.repository;

import com.jobsuite.entity.JobApplication;
import com.jobsuite.entity.TailoredResume;
import com.jobsuite.entity.TailoredResume.TailoringStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TailoredResumeRepository
        extends JpaRepository<TailoredResume, Long> {

    // Find the tailored resume for a specific job application
    // OneToOne relationship — there's exactly one per job
    Optional<TailoredResume> findByJobApplication(
            JobApplication jobApplication
    );

    // Find all tailored resumes with a specific status
    // Used by the background processing job to find
    // resumes that are PENDING and need to be processed
    List<TailoredResume> findByStatus(TailoringStatus status);

    // Check if a tailored resume already exists for a job
    boolean existsByJobApplication(JobApplication jobApplication);
}