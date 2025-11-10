package com.curihous.qbit.api.domain.journal.dto.response;

import com.curihous.qbit.domain.journal.entity.TradeEmotion;
import com.curihous.qbit.domain.journal.entity.TradeJournal;
import com.curihous.qbit.domain.order.entity.OrderRequest;
import com.curihous.qbit.domain.order.entity.OrderSide;
import com.curihous.qbit.domain.order.entity.OrderStatus;
import com.curihous.qbit.domain.stock.entity.Stock;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TradeJournalResponseDto(
    Long journalId,
    Long orderRequestId,
    String content,
    TradeEmotion tradeEmotion,
    OrderStatus orderStatus,
    OrderSide orderSide,
    String symbol,
    String stockName,
    BigDecimal executedAmount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

    public static TradeJournalResponseDto from(TradeJournal journal) {
        OrderRequest order = journal.getOrderRequest();
        Stock stock = order.getStock();

        return new TradeJournalResponseDto(
            journal.getId(),
            order.getId(),
            journal.getContent(),
            journal.getTradeEmotion(),
            order.getStatus(),
            order.getSide(),
            order.getSymbol(),
            stock.getStockName(),
            calculateExecutedAmount(order),
            journal.getCreatedAt(),
            journal.getUpdatedAt()
        );
    }

    private static BigDecimal calculateExecutedAmount(OrderRequest order) {
        BigDecimal filledAveragePrice = order.getFilledAvgPrice();
        BigDecimal filledQuantity = order.getFilledQuantity();

        if (filledAveragePrice == null || filledQuantity == null || filledQuantity.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        return filledAveragePrice.multiply(filledQuantity);
    }
}

