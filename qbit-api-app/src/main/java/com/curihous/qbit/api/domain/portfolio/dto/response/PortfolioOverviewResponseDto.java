package com.curihous.qbit.api.domain.portfolio.dto.response;

import com.curihous.qbit.common.util.TimeZoneConverter;
import com.curihous.qbit.domain.order.port.TradingPort;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaPortfolioHistoryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public record PortfolioOverviewResponseDto(
    @Schema(description = "계정 요약 정보")
    Summary summary,

    @Schema(description = "포트폴리오 히스토리 데이터 포인트 목록")
    List<HistoryPointDto> history,

    @Schema(description = "히스토리 기준 자산 가치", example = "100000.00")
    BigDecimal baseValue,

    @Schema(description = "히스토리 타임프레임", example = "1D")
    String timeframe,

    @Schema(description = "데이터 조회 시각 (KST)", example = "2025-01-15T14:10:30")
    LocalDateTime fetchedAt
) {

    public static PortfolioOverviewResponseDto from(
        TradingPort.AccountInfo accountInfo,
        AlpacaPortfolioHistoryResponse historyResponse,
        LocalDateTime fetchedAt
    ) {
        Summary summary = Summary.from(accountInfo);
        List<HistoryPointDto> historyPoints = toHistoryPoints(historyResponse);

        return new PortfolioOverviewResponseDto(
            summary,
            historyPoints,
            historyResponse != null ? historyResponse.baseValue() : null,
            historyResponse != null ? historyResponse.timeframe() : null,
            fetchedAt
        );
    }

    private static List<HistoryPointDto> toHistoryPoints(AlpacaPortfolioHistoryResponse historyResponse) {
        if (historyResponse == null || historyResponse.timestamps() == null) {
            return List.of();
        }

        List<Long> timestamps = historyResponse.timestamps();
        List<BigDecimal> equities = historyResponse.equities();
        List<BigDecimal> profitLosses = historyResponse.profitLosses();
        List<BigDecimal> profitLossPercents = historyResponse.profitLossPercents();

        List<HistoryPointDto> points = new ArrayList<>(timestamps.size());
        IntStream.range(0, timestamps.size()).forEach(index -> points.add(
            new HistoryPointDto(
                toKstMillis(timestamps.get(index)),
                getValue(equities, index),
                getValue(profitLosses, index),
                getValue(profitLossPercents, index)
            )
        ));

        return points;
    }

    private static Long toKstMillis(Long timestampSeconds) {
        if (timestampSeconds == null) {
            return null;
        }
        long utcMillis = timestampSeconds * 1000;
        return TimeZoneConverter.utcToKst(utcMillis);
    }

    private static BigDecimal getValue(List<BigDecimal> values, int index) {
        if (values == null || index >= values.size()) {
            return null;
        }
        return values.get(index);
    }

    public record Summary(
        @Schema(description = "총 자산 가치 (Equity)", example = "101250.32")
        BigDecimal equity,
        @Schema(description = "현금 잔액", example = "250.00")
        BigDecimal cash,
        @Schema(description = "포트폴리오 가치", example = "101500.00")
        BigDecimal portfolioValue,
        @Schema(description = "매수 가능 금액", example = "80000.00")
        BigDecimal buyingPower
    ) {
        public static Summary from(TradingPort.AccountInfo accountInfo) {
            if (accountInfo == null) {
                return new Summary(null, null, null, null);
            }

            return new Summary(
                parse(accountInfo.equity()),
                parse(accountInfo.cash()),
                parse(accountInfo.portfolioValue()),
                parse(accountInfo.buyingPower())
            );
        }

        private static BigDecimal parse(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    public record HistoryPointDto(
        @Schema(description = "데이터 포인트 시각 (epoch milliseconds, KST)", example = "1736932800000")
        Long timestamp,
        @Schema(description = "자산 가치 (Equity)", example = "101250.32")
        BigDecimal equity,
        @Schema(description = "손익 (금액)", example = "1250.32")
        BigDecimal profitLoss,
        @Schema(description = "손익률", example = "0.0125")
        BigDecimal profitLossPercent
    ) { }
}

