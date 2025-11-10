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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

// JPA 영속성 컨텍스트를 건드리지 않고, Alpaca API와 Redis 캐시, TradingPort만 호출하는 조정 용도라 여기에 위치시킴
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioOverviewService {

    private static final String CACHE_KEY_PREFIX = "portfolio:overview:"; // Redis 캐시 키 접두사.  portfolio:overview:123 이런 형태로 사용자별 캐시를 구분
    private static final Duration CACHE_TTL = Duration.ofSeconds(90); // Redis 캐시 만료 시간
    private static final String DEFAULT_PERIOD = "1M"; // Alpaca API 기본 기간
    private static final String DEFAULT_TIMEFRAME = "1D"; // Alpaca API 기본 타임프레임

    private final TradingPort tradingPort;
    private final AlpacaOAuthConnectionService alpacaOAuthConnectionService;
    private final AlpacaTradingPort alpacaTradingPort;
    private final RedisTemplate<String, Object> redisTemplate;

    public PortfolioOverviewResponseDto getOverview(User user) {
        String cacheKey = cacheKey(user.getId());
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof PortfolioOverviewResponseDto dto) {
            return dto;
        }

        PortfolioOverviewResponseDto overview = fetchOverview(user);
        redisTemplate.opsForValue().set(cacheKey, overview, CACHE_TTL);
        return overview;
    }

    private PortfolioOverviewResponseDto fetchOverview(User user) {
        try {
            AlpacaOAuthConnection connection = alpacaOAuthConnectionService.getValidConnection(user.getId());
            String authorization = "Bearer " + connection.getAccessToken();

            TradingPort.AccountInfo accountInfo = tradingPort.getAccountInfo(user);
            AlpacaPortfolioHistoryResponse historyResponse = alpacaTradingPort.getPortfolioHistory(
                authorization,
                DEFAULT_PERIOD,
                DEFAULT_TIMEFRAME
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

    private String cacheKey(Long userId) {
        return CACHE_KEY_PREFIX + userId;
    }
}

