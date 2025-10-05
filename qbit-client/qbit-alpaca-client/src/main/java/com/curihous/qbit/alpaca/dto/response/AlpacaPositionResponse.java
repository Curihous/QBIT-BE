package com.curihous.qbit.alpaca.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AlpacaPositionResponse {

    @JsonProperty("asset_id")
    private String assetId;

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("exchange")
    private String exchange;

    @JsonProperty("asset_class")
    private String assetClass;

    @JsonProperty("avg_entry_price")
    private String avgEntryPrice;

    @JsonProperty("qty")
    private String qty;

    @JsonProperty("side")
    private String side;

    @JsonProperty("market_value")
    private String marketValue;

    @JsonProperty("cost_basis")
    private String costBasis;

    @JsonProperty("unrealized_pl")
    private String unrealizedPl;

    @JsonProperty("unrealized_plpc")
    private String unrealizedPlpc;

    @JsonProperty("unrealized_intraday_pl")
    private String unrealizedIntradayPl;

    @JsonProperty("unrealized_intraday_plpc")
    private String unrealizedIntradayPlpc;

    @JsonProperty("current_price")
    private String currentPrice;

    @JsonProperty("lastday_price")
    private String lastdayPrice;

    @JsonProperty("change_today")
    private String changeToday;

    @JsonProperty("qty_available")
    private String qtyAvailable;
}
