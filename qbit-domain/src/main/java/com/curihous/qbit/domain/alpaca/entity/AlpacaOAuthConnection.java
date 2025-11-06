package com.curihous.qbit.domain.alpaca.entity;

import com.curihous.qbit.common.entity.BaseTimeEntity;
import com.curihous.qbit.common.encryption.EncryptedStringConverter;
import com.curihous.qbit.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "alpaca_oauth_connections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlpacaOAuthConnection extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alpaca_oauth_connection_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "alpaca_user_id", nullable = false)
    private String alpacaUserId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "token_type", nullable = false)
    private String tokenType = "Bearer";

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "is_paper_trading", nullable = false)
    private boolean isPaperTrading = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status", nullable = false)
    private AlpacaConnectionStatus alpacaConnectionStatus = AlpacaConnectionStatus.ACTIVE;
    @Builder
    public AlpacaOAuthConnection(User user, String alpacaUserId, String accessToken, 
                                String tokenType, LocalDateTime expiresAt) {
        this.user = user;
        this.alpacaUserId = alpacaUserId;
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresAt = expiresAt;
        this.isPaperTrading = true;
        this.alpacaConnectionStatus = AlpacaConnectionStatus.ACTIVE;
    }

    public void updateTokens(String accessToken, LocalDateTime expiresAt) {
        this.accessToken = accessToken;
        this.expiresAt = expiresAt;
    }

    public void disconnect() {
        this.alpacaConnectionStatus = AlpacaConnectionStatus.DISCONNECTED;
    }

    public boolean isTokenExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt.minusMinutes(50));
    }
}
