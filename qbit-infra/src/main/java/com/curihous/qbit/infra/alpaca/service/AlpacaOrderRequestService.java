package com.curihous.qbit.infra.alpaca.service;

import com.curihous.qbit.domain.stock.entity.Stock;
import com.curihous.qbit.domain.stock.repository.StockRepository;
import com.curihous.qbit.infra.alpaca.port.AlpacaTradingPort;
import com.curihous.qbit.infra.alpaca.dto.request.CreateOrderRequest;
import com.curihous.qbit.infra.alpaca.dto.request.UpdateOrderRequest;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaOrderResponse;
import com.curihous.qbit.infra.binance.client.BinanceClient;
import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.domain.alpaca.entity.AlpacaOAuthConnection;
import com.curihous.qbit.domain.alpaca.service.AlpacaOAuthConnectionService;
import com.curihous.qbit.domain.order.entity.OrderRequest;
import com.curihous.qbit.domain.order.entity.*;
import com.curihous.qbit.domain.order.port.TradingPort;
import com.curihous.qbit.domain.order.repository.OrderRequestRepository;
import com.curihous.qbit.domain.stock.port.StockPort;
import com.curihous.qbit.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Alpaca API를 통한 주식 거래(주문) 처리 Adapter
 * (Hexagonal Architecture - Adapter)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlpacaOrderRequestService implements TradingPort {

    private final OrderRequestRepository orderRequestRepository;
    private final StockRepository stockRepository;
    private final AlpacaTradingPort alpacaTradingPort;
    private final AlpacaOAuthConnectionService alpacaOAuthConnectionService;
    private final StockPort stockPort;
    private final BinanceClient binanceClient;

    // 주문 생성
    @Transactional
    public OrderRequest createOrder(User user, CreateOrderCommand command) {
        // Command를 Infra DTO로 변환
        CreateOrderRequest request = createInfraRequest(command);
        // 사용자의 활성화된 Alpaca 연결 조회
        AlpacaOAuthConnection connection = alpacaOAuthConnectionService.getValidConnection(user.getId());
        String authorization = "Bearer " + connection.getAccessToken();

        // 1. Alpaca 주문 생성
        AlpacaOrderResponse alpacaResponse;
        try {
            alpacaResponse = alpacaTradingPort.createOrder(authorization, request);
        } catch (Exception e) {
            log.error("Alpaca 주문 생성 실패: {}", e.getMessage(), e);
            throw new QbitException(ErrorCode.EXTERNAL_API_ERROR, "주문 생성에 실패했습니다: " + e.getMessage());
        }

        // 2. Stock 조회/생성 (DB에 없으면 Alpaca API로 가져옴)
        Stock stock = stockPort.getOrFetchStock(user, alpacaResponse.symbol());
        
        // 2-1. Binance 심볼 업데이트 (AI서버로 tradeCycle 차트 데이트 보낼 때 연결짓기 위함)
        updateBinanceSymbolIfNeeded(stock, command);

        // 3. OrderRequest 생성 + Stock 연결
        OrderRequest orderRequest = convertToEntity(alpacaResponse, user, stock);
        return orderRequestRepository.save(orderRequest);
    }

    // 내 주문 목록 조회 
    @Override
    @Transactional(readOnly = true)
    public Page<OrderRequest> getMyOrders(User user, Pageable pageable) {
        return orderRequestRepository.findByUser(user, pageable);
    }

    // 주문 상세 조회
    @Override
    @Transactional(readOnly = true)
    public OrderRequest getOrder(User user, Long orderId) {
        return orderRequestRepository.findByIdAndUser(orderId, user)
                .orElseThrow(() -> new QbitException(ErrorCode.ORDER_REQUEST_NOT_FOUND, "주문을 찾을 수 없습니다"));
    }
    
    // 주문 취소
    @Override
    @Transactional
    public void cancelOrder(User user, Long orderId) {
        // 사용자의 활성화된 Alpaca 연결 조회
        AlpacaOAuthConnection connection = alpacaOAuthConnectionService.getValidConnection(user.getId());
        String authorization = "Bearer " + connection.getAccessToken();
        
        try {
            // 1. 내부 ID로 기존 주문 DB에서 조회
            OrderRequest orderRequest = orderRequestRepository.findByIdAndUser(orderId, user)
                    .orElseThrow(() -> new QbitException(ErrorCode.ORDER_REQUEST_NOT_FOUND, "주문을 찾을 수 없습니다: " + orderId));
            
            String alpacaOrderId = orderRequest.getAlpacaOrderId();
            
            // 2. Alpaca API 호출 (주문 취소)
            alpacaTradingPort.cancelOrder(authorization, alpacaOrderId);
            
            // 3. DB 상태 업데이트
            orderRequest.markAsCanceled();
            orderRequestRepository.save(orderRequest);
            
            log.info("주문 취소 완료: 내부 ID={}, Alpaca ID={}", orderId, alpacaOrderId);
            
        } catch (QbitException e) {
            throw e;
        } catch (Exception e) {
            log.error("Alpaca 주문 취소 실패: orderId={}, error={}", orderId, e.getMessage(), e);
            throw new QbitException(ErrorCode.EXTERNAL_API_ERROR, "주문 취소에 실패했습니다: " + e.getMessage());
        }
    }
    
    // 포지션 조회
    @Override
    @Transactional(readOnly = true)
    public Page<TradingPort.PositionInfo> getPositions(User user, Pageable pageable) {
        // 사용자의 활성화된 Alpaca 연결 조회
        AlpacaOAuthConnection connection = alpacaOAuthConnectionService.getValidConnection(user.getId());
        String authorization = "Bearer " + connection.getAccessToken();
        
        try {
            var positions = alpacaTradingPort.getPositions(authorization);
            List<TradingPort.PositionInfo> positionInfoList = positions.stream()
                    .map(pos -> new TradingPort.PositionInfo(
                            pos.symbol(),
                            pos.quantity(),
                            pos.avgEntryPrice(),
                            pos.marketValue(),
                            pos.costBasis(),
                            pos.unrealizedPl(),
                            pos.unrealizedPlpc(),
                            pos.currentPrice(),
                            pos.side()
                    ))
                    .toList();
            
            // 페이징 처리
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), positionInfoList.size());
            
            List<TradingPort.PositionInfo> pagedPositions = positionInfoList.subList(start, end);
            return new PageImpl<>(pagedPositions, pageable, positionInfoList.size());
        } catch (Exception e) {
            log.error("Alpaca 포지션 조회 실패: {}", e.getMessage(), e);
            throw new QbitException(ErrorCode.EXTERNAL_API_ERROR, "포지션 조회에 실패했습니다: " + e.getMessage());
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
            var account = alpacaTradingPort.getAccount(authorization);
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
                    account.shortMarketValue() != null ? account.shortMarketValue().toString() : null
            );
        } catch (Exception e) {
            log.error("Alpaca 계정 정보 조회 실패: {}", e.getMessage(), e);
            throw new QbitException(ErrorCode.EXTERNAL_API_ERROR, "계정 정보 조회에 실패했습니다: " + e.getMessage());
        }
    }

    // 주문 수정
    @Override
    @Transactional
    public OrderUpdateResult updateOrder(User user, Long orderId, UpdateOrderCommand command) {

        UpdateOrderRequest request = new UpdateOrderRequest(
            parseBigDecimal(command.quantity()),
            command.timeInForce(),
            parseBigDecimal(command.limitPrice()),
            parseBigDecimal(command.stopPrice()),
            command.clientOrderId()
        );
        // 사용자의 활성화된 Alpaca 연결 조회
        AlpacaOAuthConnection connection = alpacaOAuthConnectionService.getValidConnection(user.getId());
        String authorization = "Bearer " + connection.getAccessToken();

        try {
            // 1. 내부 ID로 기존 주문(A) DB에서 조회
            OrderRequest oldOrder = orderRequestRepository.findByIdAndUser(orderId, user)
                    .orElseThrow(() -> new QbitException(ErrorCode.ORDER_REQUEST_NOT_FOUND, "주문을 찾을 수 없습니다: " + orderId));
            
            // 2. 주문 타입별 수정 가능 필드 검증
            validateUpdateRequest(oldOrder, request);
            
            String alpacaOrderId = oldOrder.getAlpacaOrderId();
            
            // 3. Alpaca API 호출 (새로운 주문 B 생성)
            AlpacaOrderResponse newOrderResponse = alpacaTradingPort.updateOrder(authorization, alpacaOrderId, request);
            
            // 4. 기존 주문(A)에 대체 정보 업데이트
            oldOrder.markAsReplaced(newOrderResponse.id(), newOrderResponse.replacedAt());
            orderRequestRepository.save(oldOrder);
            
            // 5. 새 주문(B) DB에 저장 (Stock 연결)
            Stock stock = oldOrder.getStock();  // 동일 종목이므로 기존 Stock 재사용
            OrderRequest newOrder = convertToEntity(newOrderResponse, user, stock);
            orderRequestRepository.save(newOrder);
            
            log.info("주문 수정 완료: 기존 주문 ID={}, Alpaca ID={}, 새 Alpaca 주문={}", orderId, alpacaOrderId, newOrderResponse.id());
            
            return convertToUpdateResult(newOrderResponse);
        } catch (QbitException e) {
            throw e;
        } catch (Exception e) {
            log.error("Alpaca 주문 수정 실패: orderId={}, error={}", orderId, e.getMessage(), e);
            throw new QbitException(ErrorCode.EXTERNAL_API_ERROR, "주문 수정에 실패했습니다: " + e.getMessage());
        }
    }

    // 주문 타입별 수정 가능 필드 검증
    private void validateUpdateRequest(OrderRequest oldOrder, UpdateOrderRequest request) {
        OrderType orderType = oldOrder.getType();
        
        switch (orderType) {
            case MARKET:
                // 시장가 주문: 가격 관련 필드 수정 불가
                if (request.limitPrice() != null) {
                    throw new QbitException(ErrorCode.INVALID_ORDER_PRICE, 
                        "시장가 주문에서는 지정가를 수정할 수 없습니다");
                }
                if (request.stopPrice() != null) {
                    throw new QbitException(ErrorCode.INVALID_ORDER_PRICE, 
                        "시장가 주문에서는 손절가를 수정할 수 없습니다");
                }
                break;
                
            case LIMIT:
                // 지정가 주문: stopPrice 수정 불가
                if (request.stopPrice() != null) {
                    throw new QbitException(ErrorCode.INVALID_ORDER_PRICE, 
                        "지정가 주문에서는 손절가를 수정할 수 없습니다");
                }
                break;
                
            case STOP:
                // 손절 주문: limitPrice 수정 불가
                if (request.limitPrice() != null) {
                    throw new QbitException(ErrorCode.INVALID_ORDER_PRICE, 
                        "손절 주문에서는 지정가를 수정할 수 없습니다");
                }
                break;
                
            case STOP_LIMIT:
                // 손절지정가 주문: 두 가격 모두 수정 가능
                break;
                
            default:
                log.warn("알 수 없는 주문 유형: {}", orderType);
        }
    }

    private OrderRequest convertToEntity(AlpacaOrderResponse response, User user, Stock stock) {
        return OrderRequest.builder()
                .alpacaOrderId(response.id())
                .symbol(response.symbol())
                .quantity(parseBigDecimal(response.quantity()))
                .filledQuantity(parseBigDecimal(response.filledQuantity()))
                .side(OrderSide.valueOf(response.side().toUpperCase()))
                .type(convertOrderType(response.type()))
                .timeInForce(TimeInForce.valueOf(response.timeInForce().toUpperCase()))
                .limitPrice(parseBigDecimal(response.limitPrice()))
                .stopPrice(parseBigDecimal(response.stopPrice()))
                .filledAvgPrice(parseBigDecimal(response.filledAvgPrice()))
                .status(convertOrderStatus(response.status()))
                .clientOrderId(response.clientOrderId())
                .alpacaCreatedAt(response.createdAt())
                .submittedAt(response.submittedAt())
                .filledAt(response.filledAt())
                .canceledAt(response.canceledAt())
                .replacedAt(response.replacedAt())
                .replacedBy(response.replacedBy())
                .replaces(response.replaces())
                .stock(stock) 
                .user(user)
                .build();
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.warn("BigDecimal 변환 실패: {}", value);
            return null;
        }
    }

    private OrderType convertOrderType(String type) {
        if (type == null) return OrderType.MARKET;
        return switch (type.toLowerCase()) {
            case "market" -> OrderType.MARKET;
            case "limit" -> OrderType.LIMIT;
            case "stop" -> OrderType.STOP;
            case "stop_limit" -> OrderType.STOP_LIMIT;
            default -> {
                log.warn("지원하지 않는 주문 유형: {}", type);
                yield OrderType.MARKET;
            }
        };
    }

    private OrderStatus convertOrderStatus(String status) {
        if (status == null) return OrderStatus.NEW;
        return switch (status.toLowerCase()) {
            case "new" -> OrderStatus.NEW;
            case "pending_new" -> OrderStatus.PENDING_NEW;
            case "accepted" -> OrderStatus.ACCEPTED;
            case "partially_filled" -> OrderStatus.PARTIALLY_FILLED;
            case "filled" -> OrderStatus.FILLED;
            case "done_for_day" -> OrderStatus.DONE_FOR_DAY;
            case "canceled" -> OrderStatus.CANCELED;
            case "expired" -> OrderStatus.EXPIRED;
            case "replaced" -> OrderStatus.REPLACED;
            case "pending_cancel" -> OrderStatus.PENDING_CANCEL;
            case "pending_replace" -> OrderStatus.PENDING_REPLACE;
            case "rejected" -> OrderStatus.REJECTED;
            case "suspended" -> OrderStatus.SUSPENDED;
            case "calculated" -> OrderStatus.CALCULATED;
            default -> {
                log.warn("알 수 없는 주문 상태: {}", status);
                yield OrderStatus.NEW;
            }
        };
    }
    
    private OrderUpdateResult convertToUpdateResult(AlpacaOrderResponse response) {
        return new OrderUpdateResult(
            response.id(),
            response.symbol(),
            response.quantity(),
            response.filledQuantity(),
            response.side(),
            response.type(),
            response.timeInForce(),
            response.limitPrice(),
            response.stopPrice(),
            response.filledAvgPrice(),
            response.status(),
            response.clientOrderId(),
            response.createdAt() != null ? response.createdAt().toString() : null,
            response.submittedAt() != null ? response.submittedAt().toString() : null,
            response.filledAt() != null ? response.filledAt().toString() : null,
            response.canceledAt() != null ? response.canceledAt().toString() : null,
            response.replacedAt() != null ? response.replacedAt().toString() : null,
            response.replacedBy(),
            response.replaces()
        );
    }
    
    // Command (String)를 Infra DTO (BigDecimal)로 변환하는 헬퍼 메서드
    private CreateOrderRequest createInfraRequest(CreateOrderCommand command) {
        BigDecimal quantity = parseBigDecimal(command.quantity());
        BigDecimal limitPrice = parseBigDecimal(command.limitPrice());
        BigDecimal stopPrice = parseBigDecimal(command.stopPrice());
        
        // 주문 타입에 따라 Factory 메서드 사용
        CreateOrderRequest request = switch (command.type()) {
            case "market" -> CreateOrderRequest.market(command.symbol(), command.assetClass(), quantity, command.side());
            case "limit" -> CreateOrderRequest.limit(command.symbol(), command.assetClass(), quantity, command.side(), limitPrice);
            case "stop" -> CreateOrderRequest.stop(command.symbol(), command.assetClass(), quantity, command.side(), stopPrice);
            case "stop_limit" -> CreateOrderRequest.stopLimit(command.symbol(), command.assetClass(), quantity, command.side(), stopPrice, limitPrice);
            default -> throw new QbitException(ErrorCode.INVALID_ORDER_TYPE, "지원하지 않는 주문 유형입니다: " + command.type());
        };
        
        // 모든 주문 타입에 대해 timeInForce와 clientOrderId 설정
        if (command.timeInForce() != null) {
            request.setTimeInForce(command.timeInForce());
        }
        if (command.clientOrderId() != null) {
            request.setClientOrderId(command.clientOrderId());
        }
        
        return request;
    }
    
    /**
     * Stock에 Binance 심볼이 없으면 자동으로 설정
     * - crypto: Binance API로 심볼 존재 여부 확인
     * - us_equity: null (Binance에서 거래 불가)
     */
    @Transactional
    public void updateBinanceSymbolIfNeeded(Stock stock, CreateOrderCommand command) {
        // 이미 Binance 심볼이 있으면 스킵
        if (stock.getBinanceSymbol() != null && !stock.getBinanceSymbol().isBlank()) {
            return;
        }
        
        // crypto가 아니면 스킵 (주식은 Binance 거래 불가)
        if (!"crypto".equalsIgnoreCase(command.assetClass())) {
            return;
        }
        
        try {
            // Alpaca 심볼을 Binance 형식으로 변환
            String binanceSymbol = convertToBinanceSymbol(command.symbol());
            
            // Binance API로 심볼 존재 여부 확인 (24시간 통계 조회)
            binanceClient.get24hrTicker(binanceSymbol);
            
            // 정상 조회되면 저장
            stock.setBinanceSymbol(binanceSymbol);
            stockRepository.save(stock);
            
            log.info("Binance 심볼 업데이트 완료: {} → {}", stock.getSymbol(), binanceSymbol);
            
        } catch (Exception e) {
            // Binance에 없는 심볼이면 로그만 남기고 계속 진행
            log.warn("Binance 심볼 조회 실패 (거래 불가 종목일 수 있음): symbol={}, error={}", 
                    command.symbol(), e.getMessage());
        }
    }
    
    /**
     * Alpaca 심볼을 Binance 심볼로 변환
     * BTCUSD → BTCUSDT
     * ETHUSD → ETHUSDT
     */
    private String convertToBinanceSymbol(String alpacaSymbol) {
        if (alpacaSymbol == null) {
            return null;
        }
        
        // USD로 끝나면 USDT로 변경
        if (alpacaSymbol.endsWith("USD")) {
            return alpacaSymbol.replace("USD", "USDT");
        }
        
        // 이미 USDT로 끝나면 그대로
        if (alpacaSymbol.endsWith("USDT")) {
            return alpacaSymbol;
        }
        
        // 기타 형식은 USDT 추가
        return alpacaSymbol + "USDT";
    }
}
