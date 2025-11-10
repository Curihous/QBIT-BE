package com.curihous.qbit.api.domain.ai.service;

import com.curihous.qbit.api.domain.ai.dto.response.ReportTradeCycleResponseDto;
import com.curihous.qbit.domain.order.entity.OrderRequest;
import com.curihous.qbit.domain.order.entity.OrderSide;
import com.curihous.qbit.domain.order.repository.OrderRequestRepository;
import com.curihous.qbit.domain.tradecycle.entity.TradeCycle;
import com.curihous.qbit.domain.tradecycle.service.TradeCycleService;
import com.curihous.qbit.domain.stock.entity.Stock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiDataServiceTest {

    @Mock
    private TradeCycleService tradeCycleService;
    @Mock
    private OrderRequestRepository orderRequestRepository;

    @InjectMocks
    private AiDataService aiDataService;

    // filledAt / filledAvgPrice / filledQuantity가 모두 갖춰진 주문만 tradeCycle
    // 하나라도 빠진 주문(PARTIALLY_FILLED 등)은 무시되는지 검증
    @Test
    void fetchTradePoints_excludesOrdersWithoutFillInfo() {
        // given
        long tradeCycleId = 1L;

        TradeCycle tradeCycle = mockTradeCycle(tradeCycleId);
        when(tradeCycleService.getTradeCycleById(tradeCycleId)).thenReturn(tradeCycle);

        // 정상적으로 체결 정보가 채워진 주문
        OffsetDateTime completeFilledAt = OffsetDateTime.now();
        OrderRequest completeOrder = mockOrderRequest(
            completeFilledAt,
            OrderSide.BUY,
            new BigDecimal("125.40"),
            new BigDecimal("3.5")
        );

        // 채결 정보가 미완인 주문
        OrderRequest incompleteOrder = mockOrderRequest(
            OffsetDateTime.now(),
            OrderSide.SELL,
            null,
            null
        );

        when(orderRequestRepository.findByTradeCycleIdAndStatusIn(eq(tradeCycleId), anyList()))
            .thenReturn(List.of(completeOrder, incompleteOrder));

        // when
        ReportTradeCycleResponseDto result = aiDataService.getTradeCycleForAi(tradeCycleId, "1H");

        // then
        List<ReportTradeCycleResponseDto.TradePoint> tradePoints = result.getTradePoints();
        assertThat(tradePoints).hasSize(1);

        ReportTradeCycleResponseDto.TradePoint tradePoint = tradePoints.get(0);
        assertThat(tradePoint.getPrice()).isEqualByComparingTo("125.40");
        assertThat(tradePoint.getQuantity()).isEqualByComparingTo("3.5");
        assertThat(tradePoint.getSide()).isEqualTo(OrderSide.BUY.name());
        assertThat(tradePoint.getTimestamp()).isEqualTo(
            completeFilledAt.toInstant().toEpochMilli()
        );
    }

    private TradeCycle mockTradeCycle(long id) {
        TradeCycle tradeCycle = mock(TradeCycle.class);
        Stock stock = mock(Stock.class);
        when(stock.getSymbol()).thenReturn("");

        when(tradeCycle.getId()).thenReturn(id);
        when(tradeCycle.getStock()).thenReturn(stock);
        when(tradeCycle.getStartDate()).thenReturn(LocalDateTime.of(2025, 1, 1, 0, 0));
        when(tradeCycle.getEndDate()).thenReturn(LocalDateTime.of(2025, 1, 2, 0, 0));
        return tradeCycle;
    }

    private OrderRequest mockOrderRequest(
        OffsetDateTime filledAt,
        OrderSide side,
        BigDecimal filledAvgPrice,
        BigDecimal filledQuantity
    ) {
        OrderRequest order = mock(OrderRequest.class);
        when(order.getFilledAt()).thenReturn(filledAt);
        if (filledAvgPrice != null && filledQuantity != null) {
            when(order.getSide()).thenReturn(side);
            when(order.getFilledAvgPrice()).thenReturn(filledAvgPrice);
            when(order.getFilledQuantity()).thenReturn(filledQuantity);
        } else {
            if (filledAvgPrice != null) {
                when(order.getFilledAvgPrice()).thenReturn(filledAvgPrice);
            }
            if (filledQuantity != null) {
                when(order.getFilledQuantity()).thenReturn(filledQuantity);
            }
        }
        return order;
    }
}

