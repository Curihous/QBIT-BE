package com.curihous.qbit.alpaca.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor 
@Builder 
public class UpdateOrderRequest {

    @JsonProperty("qty")
    private BigDecimal qty;

    @JsonProperty("time_in_force")
    private String timeInForce; // "day", "gtc", "ioc", "fok"

    @JsonProperty("limit_price")
    private BigDecimal limitPrice;

    @JsonProperty("stop_price")
    private BigDecimal stopPrice;

    @JsonProperty("client_order_id")
    private String clientOrderId;
}
