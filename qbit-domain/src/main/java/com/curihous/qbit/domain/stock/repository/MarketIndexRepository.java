package com.curihous.qbit.domain.stock.repository;

import com.curihous.qbit.domain.stock.entity.MarketIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MarketIndexRepository extends JpaRepository<MarketIndex, Long> {

    Optional<MarketIndex> findBySymbol(String symbol);

    // 주요 지수 목록 조회 (polygonMarketIndexService에서 country = "US", isActive = true로 설정 완료)
    @Query("SELECT m FROM MarketIndex m ORDER BY m.symbol")
    List<MarketIndex> findMajorIndices();
}
