package com.curihous.qbit.api.domain.portfolio.service;

import com.curihous.qbit.api.domain.portfolio.dto.response.PortfolioOverviewResponseDto;
import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.common.util.TimeZoneConverter;
import com.curihous.qbit.domain.alpaca.entity.AlpacaOAuthConnection;
import com.curihous.qbit.domain.alpaca.service.AlpacaOAuthConnectionService;
import com.curihous.qbit.domain.order.port.TradingPort;
import com.curihous.qbit.domain.user.entity.User;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaPortfolioHistoryResponse;
import com.curihous.qbit.infra.alpaca.port.AlpacaTradingPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;

// JPA 영속성 컨텍스트를 건드리지 않고, Alpaca API와 TradingPort만 호출하는 조정 용도라 여기에 위치시킴
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioOverviewService {

    private static final String DEFAULT_PERIOD = "1M"; // 기본값세팅
    
    // Alpaca API에서 허용하는 period 값들: 1일, 1주, 1개월, 1년
    private static final Set<String> VALID_PERIODS = Set.of("1D", "1W", "1M", "1A");
    
    // period별 기본 timeframe 매핑
    private static final Map<String, String> PERIOD_TO_TIMEFRAME = Map.of(
        "1D", "15Min",  
        "1W", "1H",  
        "1M", "1D",   
        "1A", "1D"   
    );

    private final TradingPort tradingPort;
    private final AlpacaOAuthConnectionService alpacaOAuthConnectionService;
    private final AlpacaTradingPort alpacaTradingPort;

    public PortfolioOverviewResponseDto getOverview(User user, String period) {
        // 파라미터 검증 및 기본값 설정
        String validatedPeriod = validateAndGetPeriod(period);
        String timeframe = PERIOD_TO_TIMEFRAME.get(validatedPeriod);
        
        return fetchOverview(user, validatedPeriod, timeframe);
    }

    private PortfolioOverviewResponseDto fetchOverview(User user, String period, String timeframe) {
        try {
            AlpacaOAuthConnection connection = alpacaOAuthConnectionService.getValidConnection(user.getId());
            String authorization = "Bearer " + connection.getAccessToken();

            TradingPort.AccountInfo accountInfo = tradingPort.getAccountInfo(user);
            AlpacaPortfolioHistoryResponse historyResponse = alpacaTradingPort.getPortfolioHistory(
                authorization,
                period,
                timeframe
            );

            Instant utcNow = Instant.now();
            long kstMillis = TimeZoneConverter.utcToKst(utcNow.toEpochMilli());
            LocalDateTime fetchedAtKst = LocalDateTime.ofInstant(Instant.ofEpochMilli(kstMillis), ZoneId.of("Asia/Seoul"));

            return PortfolioOverviewResponseDto.from(accountInfo, historyResponse, fetchedAtKst);
        } catch (QbitException e) {
            throw e;
        } catch (Exception e) {
            log.error("포트폴리오 오버뷰 조회 실패: userId={}, error={}", user.getId(), e.getMessage(), e);
            throw new QbitException(ErrorCode.EXTERNAL_API_ERROR, "포트폴리오 정보를 조회할 수 없습니다.");
        }
    }

    private String validateAndGetPeriod(String period) {
        if (period == null || period.isBlank()) {
            return DEFAULT_PERIOD;
        }
        String trimmed = period.trim();
        if (!VALID_PERIODS.contains(trimmed)) {
            throw new QbitException(
                ErrorCode.INVALID_INPUT_VALUE,
                String.format("유효하지 않은 period 값입니다. 허용되는 값: %s", VALID_PERIODS)
            );
        }
        return trimmed;
    }
}

