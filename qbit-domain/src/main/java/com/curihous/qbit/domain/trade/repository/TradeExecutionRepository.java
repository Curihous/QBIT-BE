package com.curihous.qbit.domain.trade.repository;

import com.curihous.qbit.domain.trade.entity.TradeExecution;
import com.curihous.qbit.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TradeExecutionRepository extends JpaRepository<TradeExecution, Long> {
    
    // 특정 사용자의 특정 종목에 대한 특정 기간 내 모든 체결 내역 조회
    @Query("SELECT DISTINCT te FROM TradeExecution te " +
           "JOIN FETCH te.orderRequest orderReq " +
           "WHERE te.user = :user " +
           "AND orderReq.symbol = :symbol " +
           "AND te.executedAt >= :startDate " +
           "AND (:endDate IS NULL OR te.executedAt <= :endDate) " +
           "ORDER BY te.executedAt ASC")
    List<TradeExecution> findByUserAndSymbolAndPeriod(
        @Param("user") User user,
        @Param("symbol") String symbol,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
}
