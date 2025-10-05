package com.curihous.qbit.domain.tradereport.entity;

import com.curihous.qbit.domain.tradecycle.entity.TradeCycle;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// TODO: 기획에 따른 엔티티 구조 수정
@Entity
@Table(name = "trade_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradeReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "trade_report_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trade_cycle_id", nullable = false)
    private TradeCycle tradeCycle;

    public TradeReport(TradeCycle tradeCycle) {
        this.tradeCycle = tradeCycle;
    }
}
