package com.curihous.qbit.domain.tradereport.repository;

import com.curihous.qbit.domain.tradereport.entity.TradeReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeReportRepository extends JpaRepository<TradeReport, Long> {
}
