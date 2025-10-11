package com.curihous.qbit.domain.stock.service;

import com.curihous.qbit.domain.stock.entity.Stock;
import com.curihous.qbit.domain.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;

    // DB에서 모든 종목 조회
    public List<Stock> getAllStocks() {
        return stockRepository.findAll();
    }

    // 특정 종목 조회
    public Optional<Stock> findBySymbol(String symbol) {
        return stockRepository.findBySymbol(symbol);
    }
    
    // symbol 또는 name으로 검색
    public List<Stock> searchStocks(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return getAllStocks(); // TODO: 인기 종목 조회로 변경
        }
        return stockRepository.searchByKeyword(keyword);
    }
}
