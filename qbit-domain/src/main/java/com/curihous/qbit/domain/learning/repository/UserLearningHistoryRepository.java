package com.curihous.qbit.domain.learning.repository;

import com.curihous.qbit.domain.learning.entity.UserLearningHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserLearningHistoryRepository extends JpaRepository<UserLearningHistory, Long> {
}
