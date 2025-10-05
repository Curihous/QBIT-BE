package com.curihous.qbit.alpaca.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AlpacaOrderResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("client_order_id")
    private String clientOrderId;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    @JsonProperty("submitted_at")
    private LocalDateTime submittedAt;

    @JsonProperty("filled_at")
    private LocalDateTime filledAt;

    @JsonProperty("expired_at")
    private LocalDateTime expiredAt;

    @JsonProperty("canceled_at")
    private LocalDateTime canceledAt;

    @JsonProperty("failed_at")
    private LocalDateTime failedAt;

    @JsonProperty("replaced_at")
    private LocalDateTime replacedAt;

    @JsonProperty("replaced_by")
    private String replacedBy;

    @JsonProperty("replaces")
    private String replaces;

    @JsonProperty("asset_id")
    private String assetId;

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("asset_class")
    private String assetClass;

    @JsonProperty("notional")
    private BigDecimal notional;

    @JsonProperty("qty")
    private String qty;

    @JsonProperty("filled_qty")
    private String filledQty;

    @JsonProperty("filled_avg_price")
    private String filledAvgPrice;

    @JsonProperty("order_class")
    private String orderClass;

    @JsonProperty("order_type")
    private String orderType;

    @JsonProperty("type")
    private String type;

    @JsonProperty("side")
    private String side;

    @JsonProperty("time_in_force")
    private String timeInForce;

    @JsonProperty("limit_price")
    private String limitPrice;

    @JsonProperty("stop_price")
    private String stopPrice;

    @JsonProperty("status")
    private String status;

    @JsonProperty("extended_hours")
    private Boolean extendedHours;

    @JsonProperty("legs")
    private List<AlpacaOrderResponse> legs;

    @JsonProperty("trail_percent")
    private String trailPercent;

    @JsonProperty("trail_price")
    private String trailPrice;

    @JsonProperty("hwm")
    private String hwm;

    @JsonProperty("commission")
    private String commission;
}
