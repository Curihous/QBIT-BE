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
}
