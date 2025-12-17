package com.curihous.qbit.api.domain.trade.performance;

import com.curihous.qbit.api.domain.trade.service.AlpacaOrderSyncService;
import com.curihous.qbit.common.event.TradeUpdateEvent;
import com.curihous.qbit.domain.order.entity.OrderRequest;
import com.curihous.qbit.domain.order.entity.OrderSide;
import com.curihous.qbit.domain.order.entity.OrderStatus;
import com.curihous.qbit.domain.order.entity.OrderType;
import com.curihous.qbit.domain.order.entity.TimeInForce;
import com.curihous.qbit.domain.order.repository.OrderRequestRepository;
import com.curihous.qbit.domain.stock.entity.Stock;
import com.curihous.qbit.domain.stock.repository.StockRepository;
import com.curihous.qbit.domain.user.entity.LoginType;
import com.curihous.qbit.domain.user.entity.User;
import com.curihous.qbit.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class AlpacaOrderSyncServiceTest {

    private static final Logger log = LoggerFactory.getLogger(AlpacaOrderSyncServiceTest.class);

    @Autowired
    private AlpacaOrderSyncService alpacaOrderSyncService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private OrderRequestRepository orderRequestRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Statistics statistics;
    private User testUser;
    private Stock testStock;
    private OrderRequest testOrder;

    @BeforeEach
    void setUp() {
        Session session = entityManager.unwrap(Session.class);
        statistics = session.getSessionFactory().getStatistics();
        statistics.clear();
        statistics.setStatisticsEnabled(true);

        testUser = createTestUser();
        testStock = createTestStock();
        testOrder = createTestOrder();
    }

    @AfterEach
    void tearDown() {
        if (statistics != null) {
            statistics.setStatisticsEnabled(false);
        }
    }

    @Test
    @DisplayName("fill 이벤트 처리 시 쿼리 수 측정")
    void testN1QueryProblem() {
        // given
        TradeUpdateEvent fillEvent = TradeUpdateEvent.of(
                testUser.getId(),
                "fill",
                testOrder.getAlpacaOrderId(),
                testStock.getSymbol(),
                "buy",
                "filled",
                "10.0",
                "150.0",
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                "10.0",
                "150.0",
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                "10.0",
                "{}"
        );

        long queryCountBefore = statistics.getQueryExecutionCount();
        long startTime = System.currentTimeMillis();

        // when
        log.info("=== fill 이벤트 처리 시작 (쿼리 로깅 활성화) ===");
        alpacaOrderSyncService.processTradeUpdate(fillEvent);
        log.info("=== fill 이벤트 처리 완료 ===");

        long endTime = System.currentTimeMillis();
        long totalQueryCount = statistics.getQueryExecutionCount() - queryCountBefore;
        long executionTime = endTime - startTime;

        // then
        log.info("=== fill 이벤트 처리 시 쿼리 수 측정 (최적화 전) ===");
        log.info("총 쿼리 실행 횟수: {}", totalQueryCount);
        log.info("처리 시간: {}ms", executionTime);
        log.info("엔티티 로드 횟수: {}", statistics.getEntityLoadCount());
        log.info("컬렉션 로드 횟수: {}", statistics.getCollectionLoadCount());
        log.info("최적화 전 쿼리 수: {}개", totalQueryCount);

        assertThat(totalQueryCount).isGreaterThan(0);
    }

    @Test
    @DisplayName("부분 체결 이벤트 처리 시 쿼리 수 측정")
    void testPartialFillQueryCount() {
        // given
        TradeUpdateEvent partialFillEvent = TradeUpdateEvent.of(
                testUser.getId(),
                "partial_fill",
                testOrder.getAlpacaOrderId(),
                testStock.getSymbol(),
                "buy",
                "partially_filled",
                "5.0",
                "150.0",
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                "5.0",
                "150.0",
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                "5.0",
                "{}"
        );

        long queryCountBefore = statistics.getQueryExecutionCount();
        long startTime = System.currentTimeMillis();

        // when
        log.info("=== partial_fill 이벤트 처리 시작 (쿼리 로깅 활성화) ===");
        alpacaOrderSyncService.processTradeUpdate(partialFillEvent);
        log.info("=== partial_fill 이벤트 처리 완료 ===");

        long endTime = System.currentTimeMillis();
        long totalQueryCount = statistics.getQueryExecutionCount() - queryCountBefore;
        long executionTime = endTime - startTime;

        // then
        log.info("=== 부분 체결 이벤트 처리 시 쿼리 수 측정 (최적화 전) ===");
        log.info("총 쿼리 실행 횟수: {}", totalQueryCount);
        log.info("처리 시간: {}ms", executionTime);
        log.info("최적화 전 쿼리 수: {}개", totalQueryCount);

        assertThat(totalQueryCount).isGreaterThan(0);
    }

    private User createTestUser() {
        User user = User.builder()
                .email("test@example.com")
                .nickname("testuser")
                .provider("google")
                .loginType(LoginType.GOOGLE)
                .build();
        return userRepository.save(user);
    }

    private Stock createTestStock() {
        Stock stock = Stock.builder()
                .symbol("AAPL")
                .stockName("Apple Inc.")
                .exchange("NASDAQ")
                .assetClass("us_equity")
                .status("active")
                .tradable(true)
                .fractionable(true)
                .build();
        return stockRepository.save(stock);
    }

    private OrderRequest createTestOrder() {
        OrderRequest order = OrderRequest.builder()
                .alpacaOrderId("test-order-id-" + System.currentTimeMillis())
                .symbol(testStock.getSymbol())
                .quantity(new BigDecimal("10.0"))
                .side(OrderSide.BUY)
                .type(OrderType.MARKET)
                .timeInForce(TimeInForce.DAY)
                .status(OrderStatus.NEW)
                .user(testUser)
                .stock(testStock)
                .alpacaCreatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .submittedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        return orderRequestRepository.save(order);
    }
}

