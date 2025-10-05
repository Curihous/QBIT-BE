package com.curihous.qbit.alpaca.service;

import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.alpaca.entity.AlpacaOAuthConnection;
import com.curihous.qbit.alpaca.repository.AlpacaOAuthConnectionRepository;
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
                                                String refreshToken, String tokenType, LocalDateTime expiresAt) {
        // 기존 연결이 있다면 토큰 정보만 업데이트
        Optional<AlpacaOAuthConnection> existingConnection = alpacaOAuthConnectionRepository.findByUser(user);
        if (existingConnection.isPresent()) {
            return updateTokens(existingConnection.get(), accessToken, refreshToken, expiresAt);
        }

        // 기존 연결이 없다면 새로 생성
        AlpacaOAuthConnection connection = AlpacaOAuthConnection.builder()
            .user(user)
            .alpacaUserId(alpacaUserId)
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .tokenType(tokenType)
            .expiresAt(expiresAt)
            .build();

        return alpacaOAuthConnectionRepository.save(connection);
    }

    // Alpaca OAuth 연결의 액세스 토큰 및 리프레시 토큰 업데이트
    @Transactional
    public AlpacaOAuthConnection updateTokens(AlpacaOAuthConnection connection, String accessToken, 
                                            String refreshToken, LocalDateTime expiresAt) {
        connection.updateTokens(accessToken, refreshToken, expiresAt);
        return alpacaOAuthConnectionRepository.save(connection);
    }

    // Alpaca OAuth 연결 해제 (연결 상태를 DISCONNECTED로 변경)
    @Transactional
    public void disconnect(AlpacaOAuthConnection connection) {
        connection.disconnect();
        alpacaOAuthConnectionRepository.save(connection);
    }

    // // 사용자의 활성화된 Alpaca 연결 조회 (User 객체 기반)
    // public AlpacaOAuthConnection getActiveConnection(User user) {
    //     return alpacaOAuthConnectionRepository.findByUser(user)
    //         .filter(connection -> connection.getConnectionStatus() == AlpacaOAuthConnection.ConnectionStatus.ACTIVE)
    //         .orElseThrow(() -> new QbitException(ErrorCode.UNAUTHORIZED));
    // }

    // // 사용자의 활성화된 Alpaca 연결 조회 (사용자 ID 기반)
    // public AlpacaOAuthConnection getActiveConnection(Long userId) {
    //     return alpacaOAuthConnectionRepository.findByUserId(userId)
    //         .filter(connection -> connection.getConnectionStatus() == AlpacaOAuthConnection.ConnectionStatus.ACTIVE)
    //         .orElseThrow(() -> new QbitException(ErrorCode.UNAUTHORIZED));
    // }
}
