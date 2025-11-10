package com.curihous.qbit.domain.journal.repository;

import com.curihous.qbit.domain.journal.entity.TradeJournal;
import com.curihous.qbit.domain.order.entity.OrderRequest;
import com.curihous.qbit.domain.order.entity.OrderSide;
import com.curihous.qbit.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TradeJournalRepository extends JpaRepository<TradeJournal, Long> {

    Optional<TradeJournal> findByUserAndOrderRequest(User user, OrderRequest orderRequest);

    Page<TradeJournal> findByUserAndCreatedAtBetween(User user, LocalDateTime startInclusive, LocalDateTime endExclusive, Pageable pageable);

    Page<TradeJournal> findByUserAndOrderRequest_SideAndCreatedAtBetween(User user, OrderSide side, LocalDateTime startInclusive, LocalDateTime endExclusive, Pageable pageable);
}

