package com.curihous.qbit.domain.tradecycle.repository;

import com.curihous.qbit.domain.stock.entity.Stock;
import com.curihous.qbit.domain.tradecycle.entity.TradeCycle;
import com.curihous.qbit.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TradeCycleRepository extends JpaRepository<TradeCycle, Long> {
    // 사용자의 특정 종목 진행 중인 사이클 조회 (endDate가 null)
    Optional<TradeCycle> findByUserAndStockAndEndDateIsNull(User user, Stock stock);
    
    // 사용자의 종료된 사이클 조회 (페이징, 최신순 정렬)
    Page<TradeCycle> findByUserAndEndDateIsNotNullOrderByEndDateDesc(User user, Pageable pageable);
}
