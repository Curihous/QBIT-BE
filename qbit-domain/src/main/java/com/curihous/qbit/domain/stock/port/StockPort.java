package com.curihous.qbit.domain.stock.port;

import com.curihous.qbit.domain.stock.entity.Stock;
import com.curihous.qbit.domain.user.entity.User;

/**
 * 주식 종목 데이터 조회를 위한 Port 인터페이스
 * (Hexagonal Architecture - Port)
 */
public interface StockPort {
    
    // 종목 조회
    Stock getOrFetchStock(User user, String symbol);
}

