package com.curihous.qbit.domain.stock.service;

import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.domain.stock.entity.MarketIndex;
import com.curihous.qbit.domain.stock.repository.MarketIndexRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketIndexService {

    private final MarketIndexRepository marketIndexRepository;
    
    // 허용된 주요 지수 심볼 목록
    private static final List<String> MAJOR_INDEX_SYMBOLS = Arrays.asList(
            "^GSPC",  // S&P 500 - 미국 전체 시장
            "^IXIC",  // NASDAQ Composite - 기술주 중심
            "^DJI",   // Dow Jones Industrial Average - 대형주 중심
            "^VIX"    // VIX (변동성 지수) - 미국 시장 변동성 지수(공포 지수)
    );

    // 주요 지수 조회 (배치 작업된 DB에서)
    public List<MarketIndex> getMajorIndices() {
        List<MarketIndex> indices = marketIndexRepository.findMajorIndices();
        if (indices.isEmpty()) {
            throw new QbitException(ErrorCode.INDEX_DATA_UNAVAILABLE, "DB에 지수 데이터가 없습니다. 배치 작업을 확인해주세요.");
        }
        return indices;
    }
    
    // 허용된 지수 심볼인지 확인
    public void validateIndexSymbol(String symbol) {
        if (!MAJOR_INDEX_SYMBOLS.contains(symbol)) {
            throw new QbitException(ErrorCode.INDEX_NOT_FOUND);
        }
    }

}
