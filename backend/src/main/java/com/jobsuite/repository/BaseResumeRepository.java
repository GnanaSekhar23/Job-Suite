package com.jobsuite.repository;

import com.jobsuite.entity.BaseResume;
import com.jobsuite.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BaseResumeRepository extends JpaRepository<BaseResume, Long> {

    List<BaseResume> findByUser(User user);

    Optional<BaseResume> findByUserAndIsActiveTrue(User user);

    boolean existsByUser(User user);

}
