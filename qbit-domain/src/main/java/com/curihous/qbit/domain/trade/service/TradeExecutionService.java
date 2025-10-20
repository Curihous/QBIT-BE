package com.curihous.qbit.domain.trade.service;

import com.curihous.qbit.domain.trade.entity.TradeExecution;
import com.curihous.qbit.domain.trade.repository.TradeExecutionRepository;
import com.curihous.qbit.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradeExecutionService {
    
    private final TradeExecutionRepository tradeExecutionRepository;
    
    // 사용자의 특정 종목에 대한 특정 기간 내 모든 체결 내역 조회
    public List<TradeExecution> getTradeExecutionsByPeriod(
        User user, 
        String symbol, 
        LocalDateTime startDate, 
        LocalDateTime endDate
    ) {
        return tradeExecutionRepository.findByUserAndSymbolAndPeriod(user, symbol, startDate, endDate);
    }
}
