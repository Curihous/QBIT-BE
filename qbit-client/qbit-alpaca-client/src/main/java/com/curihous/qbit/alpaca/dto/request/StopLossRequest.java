package com.curihous.qbit.alpaca.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class StopLossRequest {

    @JsonProperty("stop_price")
    private BigDecimal stopPrice;

    @JsonProperty("limit_price")
    private BigDecimal limitPrice;
}
