package com.curihous.qbit.domain.tradecycle.repository;

import com.curihous.qbit.domain.stock.entity.Stock;
import com.curihous.qbit.domain.tradecycle.entity.TradeCycle;
import com.curihous.qbit.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TradeCycleRepository extends JpaRepository<TradeCycle, Long> {
    // 사용자의 특정 종목 진행 중인 사이클 조회 (endDate가 null)
    Optional<TradeCycle> findByUserAndStockAndEndDateIsNull(User user, Stock stock);
    
    // 사용자의 종료된 사이클 조회 (동적 필터링: asset, 페이징, 최신순 정렬)
    @Query("SELECT tc FROM TradeCycle tc " +
           "LEFT JOIN tc.stock s " +
           "WHERE tc.user = :user AND tc.endDate IS NOT NULL " +
           "AND (:assetClass IS NULL OR s.assetClass = :assetClass) " +
           "ORDER BY tc.endDate DESC")
    Page<TradeCycle> findByUserAndEndDateIsNotNullOrderByEndDateDesc(
        @Param("user") User user,
        @Param("assetClass") String assetClass,
        Pageable pageable
    );
}
