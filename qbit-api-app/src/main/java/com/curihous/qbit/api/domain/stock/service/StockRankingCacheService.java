package com.curihous.qbit.api.domain.stock.service;

import com.curihous.qbit.api.domain.stock.dto.response.StockRankingResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * - Massive API를 사용해 계산한 랭킹 결과를 Redis에 30분간 캐시
 * - 백그라운드에서 30분마다 미리 랭킹을 계산해 둬서, 조회 시 로딩 시간을 최소화
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockRankingCacheService {

    private static final String KEY_GAINERS = "stock:ranking:gainers";
    private static final String KEY_VOLUME = "stock:ranking:volume";
    private static final String KEY_VOLATILITY = "stock:ranking:volatility";
    private static final long TTL_MINUTES = 30L;

    private final StockRankingService stockRankingService;
    private final RedisTemplate<String, Object> redisTemplate;

    // 상승률순 랭킹 조회 (캐시 우선)
    public List<StockRankingResponseDto> getCachedGainers() {
        return getOrLoad(KEY_GAINERS, () -> stockRankingService.getTopGainers(null, 20));
    }

    // 거래량순 랭킹 조회 (캐시 우선)
    public List<StockRankingResponseDto> getCachedVolumeSpikes() {
        return getOrLoad(KEY_VOLUME, () -> stockRankingService.getTopVolumeSpikes(null, 20));
    }

    // 등락폭순(변동성) 랭킹 조회 (캐시 우선)
    public List<StockRankingResponseDto> getCachedVolatility() {
        return getOrLoad(KEY_VOLATILITY, () -> stockRankingService.getTopVolatility(null, 20));
    }

    // 30분마다 백그라운드에서 랭킹을 미리 계산해 캐시에 저장
    @Scheduled(fixedDelay = TTL_MINUTES * 60_000L, initialDelay = 30_000L)
    public void refreshAllRankings() {
        try {
            log.info("주식 랭킹 캐시 리프레시 시작");
            cacheSafely(KEY_GAINERS, () -> stockRankingService.getTopGainers(null, 20));
            cacheSafely(KEY_VOLUME, () -> stockRankingService.getTopVolumeSpikes(null, 20));
            cacheSafely(KEY_VOLATILITY, () -> stockRankingService.getTopVolatility(null, 20));
            log.info("주식 랭킹 캐시 리프레시 완료");
        } catch (Exception e) {
            log.warn("주식 랭킹 캐시 리프레시 중 예기치 못한 오류 발생: {}", e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<StockRankingResponseDto> getOrLoad(String key, Supplier<List<StockRankingResponseDto>> loader) {
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof StockRankingResponseDto) {
                return (List<StockRankingResponseDto>) list;
            }
        } catch (Exception e) {
            log.warn("랭킹 캐시 조회 실패: key={}, error={}", key, e.getMessage());
        }

        // 캐시에 없으면 새로 계산 후 저장
        List<StockRankingResponseDto> fresh = safeLoad(loader);
        cache(key, fresh);
        return fresh;
    }

    private void cacheSafely(String key, Supplier<List<StockRankingResponseDto>> loader) {
        List<StockRankingResponseDto> data = safeLoad(loader);
        cache(key, data);
    }

    private List<StockRankingResponseDto> safeLoad(Supplier<List<StockRankingResponseDto>> loader) {
        try {
            List<StockRankingResponseDto> result = loader.get();
            return result != null ? result : Collections.emptyList();
        } catch (Exception e) {
            log.warn("랭킹 계산 실패, 빈 결과로 대체: error={}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private void cache(String key, List<StockRankingResponseDto> data) {
        try {
            redisTemplate.opsForValue().set(key, data, TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("랭킹 캐시 저장 실패: key={}, error={}", key, e.getMessage());
        }
    }
}


