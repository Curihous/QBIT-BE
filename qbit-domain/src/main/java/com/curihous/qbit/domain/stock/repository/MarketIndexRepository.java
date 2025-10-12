package com.curihous.qbit.domain.stock.repository;

import com.curihous.qbit.domain.stock.entity.MarketIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MarketIndexRepository extends JpaRepository<MarketIndex, Long> {

    Optional<MarketIndex> findBySymbol(String symbol);

    // DB에서 주요 지수 목록 조회 (배치 작업으로 동기화됨)
    @Query("SELECT m FROM MarketIndex m ORDER BY m.symbol")
    List<MarketIndex> findMajorIndices();
}
