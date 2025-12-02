package com.curihous.qbit.api.domain.trade.service;

import com.curihous.qbit.api.domain.trade.dto.response.MonthlyTradeStatisticsResponseDto;
import com.curihous.qbit.domain.order.entity.OrderRequest;
import com.curihous.qbit.domain.order.entity.OrderSide;
import com.curihous.qbit.domain.order.entity.OrderStatus;
import com.curihous.qbit.domain.order.repository.OrderRequestRepository;
import com.curihous.qbit.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MonthlyTradeStatisticsService {
    
    private final OrderRequestRepository orderRequestRepository;
    
    public MonthlyTradeStatisticsResponseDto getMonthlyStatistics(User user) {
        // 체결된 주문만 조회 (FILLED, PARTIALLY_FILLED)
        List<OrderStatus> filledStatuses = List.of(OrderStatus.FILLED, OrderStatus.PARTIALLY_FILLED);
        List<OrderRequest> filledOrders = orderRequestRepository.findFilledOrdersByUser(user, filledStatuses);
        
        // 월별로 그룹화
        Map<String, List<OrderRequest>> ordersByMonth = filledOrders.stream()
            .collect(Collectors.groupingBy(order -> {
                LocalDateTime filledAt = order.getFilledAt() != null
                    ? order.getFilledAt().atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                    : (order.getAlpacaCreatedAt() != null
                        ? order.getAlpacaCreatedAt().atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                        : LocalDateTime.now());
                return String.format("%04d-%02d", filledAt.getYear(), filledAt.getMonthValue());
            }));
        
        // 월별 통계 계산
        List<MonthlyTradeStatisticsResponseDto.MonthlyStatistics> statistics = new ArrayList<>();
        
        for (Map.Entry<String, List<OrderRequest>> entry : ordersByMonth.entrySet()) {
            String monthKey = entry.getKey();
            List<OrderRequest> monthOrders = entry.getValue();
            
            // 연도와 월 추출
            String[] parts = monthKey.split("-");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            
            // 매수/매도 분리
            BigDecimal totalBuyAmount = BigDecimal.ZERO;
            BigDecimal totalSellAmount = BigDecimal.ZERO;
            long tradeCount = monthOrders.size();
            
            for (OrderRequest order : monthOrders) {
                BigDecimal filledQty = order.getFilledQuantity();
                BigDecimal filledPrice = order.getFilledAvgPrice();
                
                if (filledQty == null || filledPrice == null || 
                    filledQty.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                
                BigDecimal tradeAmount = filledQty.multiply(filledPrice);
                
                if (order.getSide() == OrderSide.BUY) {
                    totalBuyAmount = totalBuyAmount.add(tradeAmount);
                } else if (order.getSide() == OrderSide.SELL) {
                    totalSellAmount = totalSellAmount.add(tradeAmount);
                }
            }
            
            // 누적손익 계산
            BigDecimal cumulativeProfitLoss = totalSellAmount.subtract(totalBuyAmount);
            
            // 수익률 계산 (매수 총액이 0보다 큰 경우만)
            BigDecimal profitRate = BigDecimal.ZERO;
            if (totalBuyAmount.compareTo(BigDecimal.ZERO) > 0) {
                profitRate = cumulativeProfitLoss
                    .divide(totalBuyAmount, 8, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100")); // 백분율로 변환
            } else if (totalSellAmount.compareTo(BigDecimal.ZERO) > 0) {
                // 매수는 없고 매도만 있는 경우 (예: 기존 보유 종목 매도)
                profitRate = BigDecimal.ZERO;
            }
            
            MonthlyTradeStatisticsResponseDto.MonthlyStatistics monthlyStats = 
                new MonthlyTradeStatisticsResponseDto.MonthlyStatistics(
                    year,
                    month,
                    tradeCount,
                    profitRate.setScale(2, RoundingMode.HALF_UP),
                    cumulativeProfitLoss.setScale(2, RoundingMode.HALF_UP)
                );
            
            statistics.add(monthlyStats);
        }
        
        // 연도, 월 순으로 정렬
        statistics.sort(Comparator
            .comparing(MonthlyTradeStatisticsResponseDto.MonthlyStatistics::year)
            .thenComparing(MonthlyTradeStatisticsResponseDto.MonthlyStatistics::month));
        
        return new MonthlyTradeStatisticsResponseDto(statistics);
    }
}

