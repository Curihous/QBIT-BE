package com.curihous.qbit.domain.alpaca.repository;

import com.curihous.qbit.domain.alpaca.entity.AlpacaConnectionStatus;
import com.curihous.qbit.domain.alpaca.entity.AlpacaOAuthConnection;
import com.curihous.qbit.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AlpacaOAuthConnectionRepository extends JpaRepository<AlpacaOAuthConnection, Long> {
    
    Optional<AlpacaOAuthConnection> findByUser(User user);
    
    Optional<AlpacaOAuthConnection> findByUserId(Long userId);
    
    boolean existsByUser(User user);
    
    // 배치 작업용: 활성 상태의 첫 번째 연결 조회
    Optional<AlpacaOAuthConnection> findFirstByAlpacaConnectionStatus(AlpacaConnectionStatus status);
}

