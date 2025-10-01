package com.curihous.qbit.domain.asset.repository;

import com.curihous.qbit.domain.asset.entity.DailyAsset;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyAssetRepository extends JpaRepository<DailyAsset, Long> {
}
