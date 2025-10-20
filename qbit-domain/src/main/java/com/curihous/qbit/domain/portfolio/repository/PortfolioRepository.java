package com.curihous.qbit.domain.portfolio.repository;

import com.curihous.qbit.domain.portfolio.entity.Portfolio;
import com.curihous.qbit.domain.stock.entity.Stock;
import com.curihous.qbit.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    // 사용자의 특정 종목 포트폴리오 조회
    Optional<Portfolio> findByUserAndStock(User user, Stock stock);
    
    // 사용자의 전체 포트폴리오 조회
    List<Portfolio> findByUser(User user);
    
    // 사용자의 종목별 포트폴리오 존재 여부
    boolean existsByUserAndStock(User user, Stock stock);
}
