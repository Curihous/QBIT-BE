package com.curihous.qbit.domain.portfolio.repository;

import com.curihous.qbit.domain.portfolio.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
}
