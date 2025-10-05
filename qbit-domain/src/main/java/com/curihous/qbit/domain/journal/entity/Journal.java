package com.curihous.qbit.domain.journal.entity;

import com.curihous.qbit.domain.tradecycle.entity.TradeCycle;
import com.curihous.qbit.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "journals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Journal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "journal_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trade_cycle_id", nullable = false)
    private TradeCycle tradeCycle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public Journal(TradeCycle tradeCycle, User user) {
        this.tradeCycle = tradeCycle;
        this.user = user;
    }
}
