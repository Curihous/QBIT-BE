package com.curihous.qbit.alpaca.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor 
@Builder 
public class CreateOrderRequest {

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("qty")
    private BigDecimal qty;

    @JsonProperty("side")
    private String side; // "buy" or "sell"

    @JsonProperty("type")
    private String type; // "market", "limit", "stop", "stop_limit"

    @JsonProperty("time_in_force")
    private String timeInForce; // "day", "gtc", "ioc", "fok"

    @JsonProperty("limit_price")
    private BigDecimal limitPrice;

    @JsonProperty("stop_price")
    private BigDecimal stopPrice;

    @JsonProperty("extended_hours")
    private Boolean extendedHours;

    @JsonProperty("client_order_id")
    private String clientOrderId;

    @JsonProperty("order_class")
    private String orderClass; // "simple", "bracket", "oco", "oto"

    @JsonProperty("take_profit")
    private TakeProfitRequest takeProfit;

    @JsonProperty("stop_loss")
    private StopLossRequest stopLoss;
}
