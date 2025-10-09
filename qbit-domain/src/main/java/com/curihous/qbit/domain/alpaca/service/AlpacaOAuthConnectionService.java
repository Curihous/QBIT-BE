package com.curihous.qbit.domain.alpaca.service;

import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.domain.alpaca.entity.AlpacaConnectionStatus;
import com.curihous.qbit.domain.alpaca.entity.AlpacaOAuthConnection;
import com.curihous.qbit.domain.alpaca.repository.AlpacaOAuthConnectionRepository;
import com.curihous.qbit.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlpacaOAuthConnectionService {

    private final AlpacaOAuthConnectionRepository alpacaOAuthConnectionRepository;

    // 사용자 ID로 Alpaca OAuth 연결 정보 조회 
    // 패턴: 단순 조회는 Optional 반환으로 처리
    public Optional<AlpacaOAuthConnection> findByUserId(Long userId) {
        return alpacaOAuthConnectionRepository.findByUserId(userId);
    }

    // 사용자의 Alpaca 연결 존재 여부 확인
    public boolean existsByUser(User user) {
        return alpacaOAuthConnectionRepository.existsByUser(user);
    }

    // 새로운 Alpaca OAuth 연결 생성
    @Transactional
    public AlpacaOAuthConnection createConnection(User user, String alpacaUserId, String accessToken, 
                                                String tokenType, LocalDateTime expiresAt) {
        // 기존 연결이 있다면 토큰 정보만 업데이트
        Optional<AlpacaOAuthConnection> existingConnection = alpacaOAuthConnectionRepository.findByUser(user);
        if (existingConnection.isPresent()) {
            return updateTokens(existingConnection.get(), accessToken, expiresAt);
        }

        // 기존 연결이 없다면 새로 생성
        AlpacaOAuthConnection connection = AlpacaOAuthConnection.builder()
            .user(user)
            .alpacaUserId(alpacaUserId)
            .accessToken(accessToken)
            .tokenType(tokenType)
            .expiresAt(expiresAt)
            .build();

        return alpacaOAuthConnectionRepository.save(connection);
    }

    // Alpaca OAuth 연결의 액세스 토큰 업데이트
    @Transactional
    public AlpacaOAuthConnection updateTokens(AlpacaOAuthConnection connection, String accessToken, 
                                            LocalDateTime expiresAt) {
        connection.updateTokens(accessToken, expiresAt);
        return alpacaOAuthConnectionRepository.save(connection);
    }

    // Alpaca OAuth 연결 해제 (연결 상태를 DISCONNECTED로 변경)
    @Transactional
    public void disconnect(AlpacaOAuthConnection connection) {
        connection.disconnect();
        alpacaOAuthConnectionRepository.save(connection);
    }

    // 사용자의 활성화된 Alpaca 연결 조회 (User 객체 기반)
    public AlpacaOAuthConnection getValidConnection(User user) {
        AlpacaOAuthConnection connection = alpacaOAuthConnectionRepository.findByUser(user)
            .orElseThrow(() -> new QbitException(ErrorCode.ALPACA_NOT_CONNECTED));
        // 연결 상태 확인
        if (connection.getAlpacaConnectionStatus() != AlpacaConnectionStatus.ACTIVE) {
            throw new QbitException(ErrorCode.ALPACA_NOT_CONNECTED);
        }
        // 토큰 만료 확인
        if (connection.isTokenExpired()) {
            throw new QbitException(ErrorCode.ALPACA_TOKEN_EXPIRED);
        }
        return connection;
    }

    // 사용자의 활성화된 Alpaca 연결 조회 (사용자 ID 기반)
    public AlpacaOAuthConnection getValidConnection(Long userId) {
        AlpacaOAuthConnection connection = alpacaOAuthConnectionRepository.findByUserId(userId)
            .orElseThrow(() -> new QbitException(ErrorCode.ALPACA_NOT_CONNECTED));
        // 연결 상태 확인
        if (connection.getAlpacaConnectionStatus() != AlpacaConnectionStatus.ACTIVE) {
            throw new QbitException(ErrorCode.ALPACA_NOT_CONNECTED);
        }
        // 토큰 만료 확인
        if (connection.isTokenExpired()) {
            throw new QbitException(ErrorCode.ALPACA_TOKEN_EXPIRED);
        }
        return connection;
    }

    // 배치 작업용: 활성 상태의 첫 번째 연결 조회 (시스템 계정)
    public Optional<AlpacaOAuthConnection> findFirstActiveConnection() {
        return alpacaOAuthConnectionRepository.findFirstByAlpacaConnectionStatus(AlpacaConnectionStatus.ACTIVE);
    }
}
