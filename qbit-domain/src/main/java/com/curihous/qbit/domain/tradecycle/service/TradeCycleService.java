package com.curihous.qbit.domain.tradecycle.service;

import com.curihous.qbit.domain.tradecycle.entity.TradeCycle;
import com.curihous.qbit.domain.tradecycle.repository.TradeCycleRepository;
import com.curihous.qbit.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradeCycleService {
    
    private final TradeCycleRepository tradeCycleRepository;
    
    // 특정 TradeCycle 조회
    public TradeCycle getTradeCycleById(Long tradeCycleId) {
        return tradeCycleRepository.findById(tradeCycleId)
            .orElseThrow(() -> new IllegalArgumentException("TradeCycle not found: " + tradeCycleId));
    }
    
    // 사용자의 종료된 TradeCycle 목록 조회 (페이징, 최신순 정렬, asset 필터링 지원)
    public Page<TradeCycle> getCompletedTradeCycles(User user, String asset, Pageable pageable) {
        String trimmedAsset = (asset != null && !asset.isBlank()) ? asset.trim() : null;
        return tradeCycleRepository.findByUserAndEndDateIsNotNullOrderByEndDateDesc(
            user, trimmedAsset, pageable);
    }
}
