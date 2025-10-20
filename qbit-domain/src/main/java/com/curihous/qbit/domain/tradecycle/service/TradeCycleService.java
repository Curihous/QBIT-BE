package com.curihous.qbit.domain.tradecycle.service;

import com.curihous.qbit.domain.tradecycle.entity.TradeCycle;
import com.curihous.qbit.domain.tradecycle.repository.TradeCycleRepository;
import lombok.RequiredArgsConstructor;
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
}
