package com.curihous.qbit.domain.trade.repository;

import com.curihous.qbit.domain.trade.entity.TradeExecution;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeExecutionRepository extends JpaRepository<TradeExecution, Long> {
}
