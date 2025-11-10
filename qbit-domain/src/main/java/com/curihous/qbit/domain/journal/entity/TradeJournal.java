package com.curihous.qbit.domain.journal.entity;

import com.curihous.qbit.common.entity.BaseTimeEntity;
import com.curihous.qbit.domain.order.entity.OrderRequest;
import com.curihous.qbit.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "trade_journals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradeJournal extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "trade_journal_id")
    private Long id;

    @Column(name = "content", nullable = false, length = 200)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "trade_emotion", nullable = false, length = 20)
    private TradeEmotion tradeEmotion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_request_id", nullable = false)
    private OrderRequest orderRequest;

    public TradeJournal(String content, TradeEmotion tradeEmotion, User user, OrderRequest orderRequest) {
        this.content = content;
        this.tradeEmotion = tradeEmotion;
        this.user = user;
        this.orderRequest = orderRequest;
    }

    public void update(String content, TradeEmotion tradeEmotion) {
        this.content = content;
        this.tradeEmotion = tradeEmotion;
    }
}

