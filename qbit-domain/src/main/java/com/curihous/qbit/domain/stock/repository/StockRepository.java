package com.curihous.qbit.domain.stock.repository;

import com.curihous.qbit.domain.stock.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface StockRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findBySymbol(String symbol);
    List<Stock> findBySymbolIn(Set<String> symbols);
    // S&P500 구성 종목 조회 (assetClass 대소문자 무시)
    List<Stock> findBySp500TrueAndAssetClassIgnoreCase(String assetClass);
    
    // symbol 또는 name으로 검색, 대소문자 무시
    @Query("SELECT s FROM Stock s WHERE " +
           "LOWER(s.symbol) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(s.stockName) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Stock> searchByKeyword(@Param("keyword") String keyword);
}
