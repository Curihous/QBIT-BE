package com.curihous.qbit.alpaca.repository;

import com.curihous.qbit.alpaca.entity.AlpacaOAuthConnection;
import com.curihous.qbit.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AlpacaOAuthConnectionRepository extends JpaRepository<AlpacaOAuthConnection, Long> {
    
    Optional<AlpacaOAuthConnection> findByUser(User user);
    
    Optional<AlpacaOAuthConnection> findByUserId(Long userId);
    
    boolean existsByUser(User user);
}
