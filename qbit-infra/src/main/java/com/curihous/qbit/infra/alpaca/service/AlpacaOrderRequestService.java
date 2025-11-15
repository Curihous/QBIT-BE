package com.curihous.qbit.infra.alpaca.service;

import com.curihous.qbit.domain.stock.entity.Stock;
import com.curihous.qbit.infra.alpaca.port.AlpacaTradingPort;
import com.curihous.qbit.infra.alpaca.dto.request.CreateOrderRequest;
import com.curihous.qbit.infra.alpaca.dto.request.UpdateOrderRequest;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaOrderResponse;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaPositionResponse;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaAccountResponse;
import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.domain.alpaca.entity.AlpacaOAuthConnection;
import com.curihous.qbit.domain.alpaca.service.AlpacaOAuthConnectionService;
import com.curihous.qbit.domain.order.entity.OrderRequest;
import com.curihous.qbit.domain.order.entity.*;
import com.curihous.qbit.domain.order.port.TradingPort;
import com.curihous.qbit.domain.order.repository.OrderRequestRepository;
import com.curihous.qbit.domain.user.entity.User;
import com.curihous.qbit.common.util.CryptoSymbolConverter;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.curihous.qbit.domain.order.entity.OrderSide;
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
public class AlpacaOrderRequestService {

    private final OrderRequestRepository orderRequestRepository;
    private final AlpacaTradingPort alpacaTradingPort;
    private final AlpacaOAuthConnectionService alpacaOAuthConnectionService;
    private final AlpacaStockService alpacaStockService;

    // 주문 생성
    @Transactional
    public TradingPort.OrderCreatedResult createOrder(User user, TradingPort.CreateOrderCommand command) {
        // Command를 Infra DTO로 변환
        CreateOrderRequest request = createInfraRequest(command);
        // 사용자의 활성화된 Alpaca 연결 조회
        AlpacaOAuthConnection connection = alpacaOAuthConnectionService.getValidConnection(user.getId());
        String authorization = "Bearer " + connection.getAccessToken();

        // 1. Alpaca 주문 생성
        AlpacaOrderResponse alpacaResponse;
        try {
            alpacaResponse = alpacaTradingPort.createOrder(authorization, request);
        } catch (FeignException.Forbidden e) {
            // 403 Forbidden 에러 처리 
            String errorMessage = e.contentUTF8();
            if (errorMessage != null && (
                errorMessage.contains("potential wash trade") || 
                errorMessage.contains("opposite side") ||
                errorMessage.contains("existing_order_id")
            )) {
                log.error("Alpaca 주문 생성 실패 (반대쪽 주문 존재): {}", errorMessage);
                throw new QbitException(ErrorCode.OPPOSITE_SIDE_ORDER_EXISTS);
            }
            log.error("Alpaca 주문 생성 실패 (403 Forbidden): {}", errorMessage);
            throw new QbitException(ErrorCode.EXTERNAL_API_ERROR, "주문 생성에 실패했습니다: " + errorMessage);
        } catch (Exception e) {
            log.error("Alpaca 주문 생성 실패: {}", e.getMessage(), e);
            throw new QbitException(ErrorCode.EXTERNAL_API_ERROR, "주문 생성에 실패했습니다: " + e.getMessage());
        }

        // 2. Stock 조회/생성 (DB에 없으면 Alpaca API로 가져옴)
        Stock stock = alpacaStockService.getOrFetchStock(user, alpacaResponse.symbol());

        // 3. OrderRequest 생성 + Stock 연결
        OrderRequest orderRequest = convertToEntity(alpacaResponse, user, stock);
        orderRequestRepository.save(orderRequest);
        
        // 4. Alpaca 최신 정보로 주문 동기화(WS도 있지만 안전장치)
        refreshOrderFromAlpaca(authorization, alpacaResponse.id());

        // 5. OrderCreatedResult 반환
        return convertToCreatedResult(alpacaResponse);
    }

    // 내 주문 목록 조회
    @Transactional(readOnly = true)
    public Page<OrderRequest> getMyOrders(User user, String symbol, String side, Pageable pageable) {
        // side를 enum으로 변환
        OrderSide orderSide = null;
        if (side != null && !side.isBlank()) {
            try {
                orderSide = OrderSide.valueOf(side.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new QbitException(
                    ErrorCode.INVALID_INPUT_VALUE, 
                    "잘못된 side 값입니다. BUY 또는 SELL만 가능합니다."
                );
            }
        }
        
        // symbol 처리
        String trimmedSymbol = (symbol != null && !symbol.isBlank()) ? symbol.trim() : null;
        boolean hasSymbol = trimmedSymbol != null;
        boolean hasSide = orderSide != null;
        
        // symbol과 side 조합에 따라 적절한 메서드 호출
        if (hasSymbol && hasSide) {
            return orderRequestRepository.findByUserAndSymbolAndSide(user, trimmedSymbol, orderSide, pageable);
        } else if (hasSymbol) {
            return orderRequestRepository.findByUserAndSymbol(user, trimmedSymbol, pageable);
        } else if (hasSide) {
            return orderRequestRepository.findByUserAndSide(user, orderSide, pageable);
        } else {
            return orderRequestRepository.findByUser(user, pageable);
        }
    }

    // 주문 상세 조회
    @Transactional(readOnly = true)
    public OrderRequest getOrder(User user, Long orderId) {
        return orderRequestRepository.findByIdAndUser(orderId, user)
                .orElseThrow(() -> new QbitException(ErrorCode.ORDER_REQUEST_NOT_FOUND, "주문을 찾을 수 없습니다"));
    }
    
    // Alpaca 주문 ID로 주문 조회
    @Transactional(readOnly = true)
    public OrderRequest getOrderByAlpacaOrderId(User user, String alpacaOrderId) {
        return orderRequestRepository.findByAlpacaOrderIdAndUser(alpacaOrderId, user)
                .orElseThrow(() -> new QbitException(ErrorCode.ORDER_REQUEST_NOT_FOUND, "주문을 찾을 수 없습니다"));
    }
    
    // 주문 취소
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
    @Transactional(readOnly = true)
    public Page<TradingPort.PositionInfo> getPositions(User user, Pageable pageable) {
        // 사용자의 활성화된 Alpaca 연결 조회
        AlpacaOAuthConnection connection = alpacaOAuthConnectionService.getValidConnection(user.getId());
        String authorization = "Bearer " + connection.getAccessToken();
        
        try {
            var positions = alpacaTradingPort.getPositions(authorization);
            List<TradingPort.PositionInfo> positionInfoList = positions.stream()
                    .map(pos -> {
                        String originalSymbol = CryptoSymbolConverter.alpacaPositionToAssetFormat(pos.symbol());
                        return new TradingPort.PositionInfo(
                                originalSymbol, // 심볼
                                pos.quantity(), // 보유 수량
                                pos.avgEntryPrice(), // 평균 매수가
                                pos.marketValue(), // 시장 가치
                                pos.costBasis(), // 원가 기준
                                pos.unrealizedPl(), // 미실현 손익
                                pos.unrealizedPlpc(), // 미실현 손익률
                                pos.currentPrice(), // 현재 가격
                                pos.side() // 포지션 방향
                        );
                    })
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
    
    // 특정 종목 포지션 조회 - 주문에서 필요한 정보 제공
    @Transactional(readOnly = true)
    public TradingPort.SimplePositionWithAccountInfo getPositionBySymbol(User user, String symbol) {
        AlpacaOAuthConnection connection = alpacaOAuthConnectionService.getValidConnection(user.getId());
        String authorization = "Bearer " + connection.getAccessToken();
        
        try {
            // Alpaca Positions API는 crypto 심볼을 슬래시 없이 사용 (ETH/USD → ETHUSD)
            String positionSymbol = CryptoSymbolConverter.alpacaAssetToPositionFormat(symbol);
            
            // 포지션 조회
            AlpacaPositionResponse positionResponse = alpacaTradingPort.getPosition(authorization, positionSymbol);
            
            TradingPort.SimplePositionInfo positionInfo = new TradingPort.SimplePositionInfo(
                    positionResponse.symbol(),
                    positionResponse.quantity(),
                    positionResponse.side()
            );
            
            // 계정 정보 조회 - buyingPower만 사용
            AlpacaAccountResponse accountResponse = alpacaTradingPort.getAccount(authorization);
            TradingPort.SimpleAccountInfo accountInfo = new TradingPort.SimpleAccountInfo(
                    accountResponse.buyingPower() != null ? accountResponse.buyingPower().toString() : null
            );
            
            return new TradingPort.SimplePositionWithAccountInfo(positionInfo, accountInfo);
        } catch (FeignException.NotFound e) {
            log.warn("포지션을 찾을 수 없음: symbol={}", symbol);
            throw new QbitException(ErrorCode.ORDER_REQUEST_NOT_FOUND, "해당 symbol을 보유하고 있지 않습니다: " + symbol);
        } catch (Exception e) {
            log.error("Alpaca 포지션 조회 실패: symbol={}, error={}", symbol, e.getMessage(), e);
            throw new QbitException(ErrorCode.EXTERNAL_API_ERROR, "포지션 조회에 실패했습니다: " + e.getMessage());
        }
    }

    // 주문 수정
    @Transactional
    public TradingPort.OrderUpdateResult updateOrder(User user, Long orderId, TradingPort.UpdateOrderCommand command) {

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
    
    private TradingPort.OrderUpdateResult convertToUpdateResult(AlpacaOrderResponse response) {
        return new TradingPort.OrderUpdateResult(
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
    
    private TradingPort.OrderCreatedResult convertToCreatedResult(AlpacaOrderResponse response) {
        return new TradingPort.OrderCreatedResult(
            response.id(),
            response.symbol(),
            response.quantity(),
            response.side(),
            response.type(),
            response.status(),
            response.timeInForce(),
            response.limitPrice(),
            response.stopPrice(),
            response.clientOrderId(),
            response.createdAt() != null ? response.createdAt().toString() : null
        );
    }
    
    // Command (String)를 Infra DTO (BigDecimal)로 변환하는 헬퍼 메서드
    private CreateOrderRequest createInfraRequest(TradingPort.CreateOrderCommand command) {
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

    private void refreshOrderFromAlpaca(String authorization, String alpacaOrderId) {
        int attempts = 0;
        long delayMillis = 250;
        while (attempts < 8) {
            try {
                AlpacaOrderResponse fetched = alpacaTradingPort.getOrder(authorization, alpacaOrderId);
                if (fetched != null) {
                    orderRequestRepository.findByAlpacaOrderId(alpacaOrderId).ifPresent(order -> {
                        OrderStatus currentStatus = order.getStatus();
                        OrderStatus fetchedStatus = convertOrderStatus(fetched.status());

                        if (shouldAdvanceStatus(currentStatus, fetchedStatus)) {
                            order.updateStatus(fetchedStatus);
                        }

                        order.updateFilledInfo(
                                parseBigDecimal(fetched.filledQuantity()),
                                parseBigDecimal(fetched.filledAvgPrice()),
                                fetched.filledAt()
                        );

                        if (fetched.submittedAt() != null) {
                            order.updateSubmittedAt(fetched.submittedAt());
                        }
                        if (fetched.filledAt() != null) {
                            order.updateFilledAt(fetched.filledAt());
                        }
                        if (fetched.canceledAt() != null) {
                            order.updateCanceledAt(fetched.canceledAt());
                        }
                        if (fetched.rejectedAt() != null) {
                            order.updateRejectedAt(fetched.rejectedAt());
                        }
                        if (fetched.expiredAt() != null) {
                            order.updateExpiredAt(fetched.expiredAt());
                        }

                        orderRequestRepository.save(order);
                    });
                    if ("filled".equalsIgnoreCase(fetched.status())
                            || "partially_filled".equalsIgnoreCase(fetched.status())) {
                        return;
                    }
                    continue;
                }
                return;
            } catch (FeignException.NotFound e) {
                log.debug("Alpaca 주문 단건 조회 재시도: orderId={}, attempt={}", alpacaOrderId, attempts + 1);
            } catch (Exception e) {
                log.warn("Alpaca 주문 단건 조회 실패: orderId={}, attempt={}, error={}", alpacaOrderId, attempts + 1, e.getMessage());
            }
            attempts++;
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            delayMillis = Math.min(delayMillis * 2, 1500);
        }
        log.warn("Alpaca 주문 단건 조회 취소: orderId={}, 최대 시도 횟수 초과", alpacaOrderId);
    }

    private boolean shouldAdvanceStatus(OrderStatus current, OrderStatus incoming) {
        if (incoming == null) {
            return false;
        }
        if (current == null) {
            return true;
        }
        if (incoming == current) {
            return false;
        }
        if (isTerminalStatus(current) && !isTerminalStatus(incoming)) {
            return false;
        }
        if (isTerminalStatus(incoming)) {
            return true;
        }
        return incoming.ordinal() >= current.ordinal();
    }

    private boolean isTerminalStatus(OrderStatus status) {
        return switch (status) {
            case FILLED, CANCELED, EXPIRED, REPLACED, REJECTED, DONE_FOR_DAY, CALCULATED -> true;
            default -> false;
        };
    }
}
