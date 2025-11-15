package com.curihous.qbit.infra.alpaca.service;

import com.curihous.qbit.infra.alpaca.client.AlpacaOAuthClient;
import com.curihous.qbit.infra.alpaca.client.AlpacaTradingClient;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaAccountResponse;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaTokenResponse;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.domain.alpaca.entity.AlpacaOAuthConnection;
import com.curihous.qbit.domain.alpaca.service.AlpacaOAuthConnectionService;
import com.curihous.qbit.domain.user.entity.User;
import com.curihous.qbit.domain.user.service.UserService;
import com.curihous.qbit.domain.order.port.TradingPort;
import com.curihous.qbit.domain.order.entity.OrderRequest;
import com.curihous.qbit.common.event.LoginOrderSyncEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlpacaOAuthService implements TradingPort {

    @Value("${alpaca.oauth.client-id}")
    private String clientId;

    @Value("${alpaca.oauth.redirect-uri}")
    private String redirectUri;

    @Value("${alpaca.oauth.authorization-url}")
    private String authorizationUrl;

    private final AlpacaOAuthClient alpacaOAuthClient;
    private final AlpacaTradingClient alpacaTradingClient;
    private final AlpacaOAuthConnectionService alpacaOAuthConnectionService;
    private final UserService userService;
    private final AlpacaOAuthStateService alpacaOAuthStateService;
    private final AlpacaOrderRequestService alpacaOrderRequestService;
    private final ApplicationEventPublisher eventPublisher;
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String LOGIN_SYNC_STREAM = "login-order-sync";

    // OAuth 승인 URL 생성
    public String generateAuthUrl(Long userId) {
        String secureState = alpacaOAuthStateService.generateSecureState(userId);
        return UriComponentsBuilder.fromUriString(authorizationUrl)
            .queryParam("response_type", "code")
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("scope", "trading")
            .queryParam("state", secureState)
            .queryParam("env", "paper") // Paper Trading
            .build()
            .toUriString();
    }

    // OAuth 상태값 검증 및 사용자 ID 추출
    public Long validateStateAndExtractUserId(String state) {
        return alpacaOAuthStateService.validateAndExtractUserId(state);
    }

    // 인증 코드를 액세스 토큰으로 교환하고 DB에 저장
    @Transactional
    public AlpacaOAuthConnection exchangeTokenAndSave(Long userId, String code) {
        try {
            AlpacaTokenResponse tokenResponse = alpacaOAuthClient.exchangeToken(
                code, clientId, redirectUri
            );

            String alpacaUserId = getAlpacaUserIdFromAccount(tokenResponse.accessToken());
            User user = userService.findById(userId);

            long expiresInSeconds = tokenResponse.expiresIn() != null ?
                    tokenResponse.expiresIn() : 604800L;
            // TODO: alpaca가 모의투자라서 제한을 두지 않는것같으므로.. 추후 보안을 위해 주기적 재인증 로직 추가 필요
            LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC)
                .plusSeconds(expiresInSeconds);

            AlpacaOAuthConnection connection = alpacaOAuthConnectionService.createConnection(
                user,
                alpacaUserId,
                tokenResponse.accessToken(),
                tokenResponse.tokenType(),
                expiresAt
            );

            // 트랜잭션 커밋 후 로그인 시 주문 상태 동기화 이벤트 발행
            String finalAccessToken = tokenResponse.accessToken();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.info("트랜잭션 커밋 완료, 주문 동기화 이벤트 발행: userId={}", user.getId());
                    
                    // Spring 이벤트 (qbit-api-app 내부 처리용): 토큰을 Redis에 저장 + 주문 내역을 DB에 동기화
                    eventPublisher.publishEvent(new LoginOrderSyncEvent(user.getId(), user.getEmail(), finalAccessToken));
                    
                    // Redis Streams 이벤트 (qbit-realtime-app 처리용): Alpaca WebSocket 연결 및 실시간 거래 업데이트 구독
                    Map<String, String> fields = new HashMap<>();
                    fields.put("userId", String.valueOf(user.getId()));
                    fields.put("userEmail", user.getEmail());
                    fields.put("hasAlpacaToken", "true"); // 토큰은 Redis에 이미 저장되어 있음 (Spring 이벤트에서 저장)
                    
                    try {
                        redisTemplate.opsForStream().add(LOGIN_SYNC_STREAM, fields);
                        log.info("LoginOrderSyncEvent Redis Streams 발행 완료: userId={}", user.getId());
                    } catch (Exception e) {
                        log.error("LoginOrderSyncEvent Redis Streams 발행 실패: userId={}, error={}", 
                                user.getId(), e.getMessage(), e);
                    }
                }
            });

            return connection;

        } catch (Exception e) {
            throw new QbitException(ErrorCode.OAUTH2_LOGIN_FAILED);
        }
    }

    private String getAlpacaUserIdFromAccount(String accessToken) {
        String bearerToken = "Bearer " + accessToken;
        
        try {
            AlpacaAccountResponse accountInfo = alpacaTradingClient.getAccount(bearerToken);
            return accountInfo.id();
            
        } catch (Exception e) {
            throw new QbitException(ErrorCode.EXTERNAL_API_ERROR, 
                "Alpaca 계정 정보 조회에 실패했습니다. Paper Trading 계정이 활성화되어 있는지 확인해주세요.");
        }
    }

    // OAuth 연결 해제
    @Transactional
    public void disconnect(Long userId) {
        Optional<AlpacaOAuthConnection> connectionOpt = alpacaOAuthConnectionService.findByUserId(userId);
        if (connectionOpt.isPresent()) {
            alpacaOAuthConnectionService.disconnect(connectionOpt.get());
        }
    }
    

    // 계정 정보 조회
    @Override
    @Transactional(readOnly = true)
    public TradingPort.AccountInfo getAccountInfo(User user) {
        // 사용자의 활성화된 Alpaca 연결 조회
        AlpacaOAuthConnection connection = alpacaOAuthConnectionService.getValidConnection(user.getId());
        String authorization = "Bearer " + connection.getAccessToken();
        
        try {
            var account = alpacaTradingClient.getAccount(authorization);
            return new TradingPort.AccountInfo(
                    account.accountNumber(),
                    account.status(),
                    account.currency(),
                    account.buyingPower() != null ? account.buyingPower().toString() : null,
                    account.cash() != null ? account.cash().toString() : null,
                    account.portfolioValue() != null ? account.portfolioValue().toString() : null,
                    account.equity() != null ? account.equity().toString() : null,
                    account.lastEquity() != null ? account.lastEquity().toString() : null,
                    account.longMarketValue() != null ? account.longMarketValue().toString() : null,
                    account.shortMarketValue() != null ? account.shortMarketValue().toString() : null,
                    account.id(),                    
                    account.cryptoStatus(),           
                    account.tradingBlocked(),        
                    account.accountBlocked(),
                    account.multiplier()         
            );
        } catch (Exception e) {
            log.error("Alpaca 계정 정보 조회 실패: {}", e.getMessage(), e);
            throw new QbitException(ErrorCode.EXTERNAL_API_ERROR, "계정 정보 조회에 실패했습니다: " + e.getMessage());
        }
    }

    @Override
    public TradingPort.OrderCreatedResult createOrder(User user, TradingPort.CreateOrderCommand command) {
        return alpacaOrderRequestService.createOrder(user, command);
    }

    @Override
    public Page<OrderRequest> getMyOrders(User user, Pageable pageable) {
        return alpacaOrderRequestService.getMyOrders(user, pageable);
    }
    
    @Override
    public Page<OrderRequest> getMyOrdersBySymbol(User user, String symbol, Pageable pageable) {
        return alpacaOrderRequestService.getMyOrdersBySymbol(user, symbol, pageable);
    }

    @Override
    public OrderRequest getOrder(User user, Long orderId) {
        return alpacaOrderRequestService.getOrder(user, orderId);
    }

    @Override
    public OrderRequest getOrderByAlpacaOrderId(User user, String alpacaOrderId) {
        return alpacaOrderRequestService.getOrderByAlpacaOrderId(user, alpacaOrderId);
    }

    @Override
    public void cancelOrder(User user, Long orderId) {
        alpacaOrderRequestService.cancelOrder(user, orderId);
    }

    @Override
    public Page<TradingPort.PositionInfo> getPositions(User user, Pageable pageable) {
        return alpacaOrderRequestService.getPositions(user, pageable);
    }
    
    @Override
    public TradingPort.SimplePositionWithAccountInfo getPositionBySymbol(User user, String symbol) {
        return alpacaOrderRequestService.getPositionBySymbol(user, symbol);
    }

    @Override
    public TradingPort.OrderUpdateResult updateOrder(User user, Long orderId, TradingPort.UpdateOrderCommand command) {
        return alpacaOrderRequestService.updateOrder(user, orderId, command);
    }

}

