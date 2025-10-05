package com.curihous.qbit.alpaca.entity;

import com.curihous.qbit.common.entity.BaseTimeEntity;
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
    private Long alpacaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "alpaca_user_id", nullable = false)
    private String alpacaUserId;

    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken; // 암호화해서 저장

    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken; // 암호화해서 저장

    @Column(name = "token_type", nullable = false)
    private String tokenType = "Bearer";

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "is_paper_trading", nullable = false)
    private boolean isPaperTrading = true; // Paper Trading 고정

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status", nullable = false)
    private ConnectionStatus connectionStatus = ConnectionStatus.ACTIVE;

    @Builder
    public AlpacaOAuthConnection(User user, String alpacaUserId, String accessToken, 
                                String refreshToken, String tokenType, LocalDateTime expiresAt) {
        this.user = user;
        this.alpacaUserId = alpacaUserId;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = tokenType;
        this.expiresAt = expiresAt;
        this.isPaperTrading = true;
        this.connectionStatus = ConnectionStatus.ACTIVE;
    }

    public void updateTokens(String accessToken, String refreshToken, LocalDateTime expiresAt) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAt = expiresAt;
    }

    public void disconnect() {
        this.connectionStatus = ConnectionStatus.DISCONNECTED;
    }

    public boolean isTokenExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt.minusMinutes(50)); // 50분 여유
    }

    public enum ConnectionStatus {
        ACTIVE, DISCONNECTED, EXPIRED, ERROR
    }
}
