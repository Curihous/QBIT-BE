package com.curihous.qbit.domain.journal.repository;

import com.curihous.qbit.domain.journal.entity.Journal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalRepository extends JpaRepository<Journal, Long> {
}
