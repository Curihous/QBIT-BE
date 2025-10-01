package com.curihous.qbit.domain.stock.repository;

import com.curihous.qbit.domain.stock.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<Stock, Long> {
}
