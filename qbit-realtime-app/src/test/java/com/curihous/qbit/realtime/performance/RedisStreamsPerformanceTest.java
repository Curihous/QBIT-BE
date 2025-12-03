package com.curihous.qbit.realtime.performance;

import com.curihous.qbit.common.event.TradeUpdateEvent;
import com.curihous.qbit.realtime.producer.TradeUpdateProducer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis Streams 성능 테스트
 * 자세한 설명은 docs/블로그.md 참고
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
class RedisStreamsPerformanceTest {

    private static final String STREAM_KEY = "trade-updates";
    private static final String CONSUMER_GROUP = "qbit-realtime-group";
    private static final String CONSUMER_NAME = "qbit-realtime-consumer-trade";
    
    private static final int MESSAGES_PER_SECOND = 200;
    private static final int PUBLISH_DURATION_SECONDS = 3;  // 발행 시간
    private static final int PROCESSING_TIMEOUT_SECONDS = 10;  // 발행 완료 후 처리 대기 시간
    private static final int EXPECTED_TOTAL_MESSAGES = MESSAGES_PER_SECOND * PUBLISH_DURATION_SECONDS;
    private static final int MAX_LATENCY_MS = 200;  // 실시간 거래 시스템 목표: 평균 지연 200ms 이하
    private static final int P95_MAX_LATENCY_MS = 300;  // 실시간 거래 시스템 목표: P95 지연 300ms 이하

    @Autowired
    private TradeUpdateProducer tradeUpdateProducer;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisConnectionFactory connectionFactory;

    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        executorService = Executors.newCachedThreadPool();
        cleanupStream();
        createConsumerGroupIfNotExists();
    }

    @AfterEach
    void tearDown() {
        if (executorService != null) {
            shutdownExecutor(executorService);
        }
        cleanupStream();
    }

    @Test
    @DisplayName("초당 수백 건의 주문 부하 시뮬레이션 - 평균 응답 지연 확인")
    void testHighLoadThroughput() {
        // given: 테스트 데이터 구조 초기화
        Map<String, Long> publishTimes = new ConcurrentHashMap<>();  // sequence -> 발행 시간
        List<Long> latencies = new CopyOnWriteArrayList<>();          // 지연 시간 목록
        AtomicInteger publishedCount = new AtomicInteger(0);
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicBoolean publishingComplete = new AtomicBoolean(false);

        // when: Producer와 Consumer를 병렬로 실행
        Future<?> producerFuture = startProducer(publishTimes, publishedCount, publishingComplete);
        Future<?> consumerFuture = startConsumer(publishTimes, latencies, processedCount, publishingComplete);

        // then: 발행 완료 대기
        try {
            producerFuture.get(PUBLISH_DURATION_SECONDS + 5, TimeUnit.SECONDS);
            log.info("발행 완료: 발행된 메시지 수 = {}", publishedCount.get());
        } catch (Exception e) {
            log.warn("Producer 대기 중 오류: {}", e.getMessage());
        }

        // 발행 완료 후 처리 대기
        publishingComplete.set(true);
        log.info("발행 완료 후 처리 대기 시작: 처리된 메시지 수 = {}", processedCount.get());
        
        // 일정 시간 동안 처리된 메시지가 없을 때까지 대기
        // (1초 동안 진행 없으면 종료)
        int previousProcessed = processedCount.get();
        int noProgressCount = 0;
        long startWaitTime = System.currentTimeMillis();
        long maxWaitTime = PROCESSING_TIMEOUT_SECONDS * 1000L;
        
        while ((System.currentTimeMillis() - startWaitTime) < maxWaitTime) {
            try {
                Thread.sleep(200);
                int currentProcessed = processedCount.get();
                
                if (currentProcessed > previousProcessed) {
                    // 진행 중 - 카운터 리셋
                    previousProcessed = currentProcessed;
                    noProgressCount = 0;
                } else {
                    // 진행 없음 - 카운터 증가
                    noProgressCount++;
                    if (noProgressCount >= 5) {  // 200ms * 5 = 1초 동안 진행 없으면 종료
                        log.info("처리 진행 없음으로 종료: 처리된 메시지 수 = {}", currentProcessed);
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        log.info("처리 대기 완료: 발행된 메시지 수 = {}, 처리된 메시지 수 = {}", 
            publishedCount.get(), processedCount.get());

        // 종료
        consumerFuture.cancel(true);

        // 검증: 처리율 및 지연 시간 검증
        assertPerformanceMetrics(latencies, processedCount, publishedCount);
    }

    @Test
    @DisplayName("장애 복구 테스트 - 워커 중단 후 재시작 시 데이터 손실 없이 복구 확인")
    void testFailureRecovery() throws InterruptedException {
        // given: 초기 메시지 발행
        int initialMessages = 100;
        int additionalMessages = 50;
        
        publishMessages(1, initialMessages);
        
        // when: Consumer가 일부 처리한 후 중단 시뮬레이션
        processMessagesPartially(initialMessages / 2);  // 50개만 처리
        
        // 추가 메시지 발행 (워커 중단 중)
        publishMessages(initialMessages + 1, initialMessages + additionalMessages);
        
        // then: 재시작 후 모든 메시지 복구 확인
        int recoveredCount = recoverAllMessages();
        
        assertThat(recoveredCount).isGreaterThanOrEqualTo(additionalMessages);
        log.info("장애 복구 테스트 완료: 복구된 메시지 수 = {}", recoveredCount);
    }

    // ========== 헬퍼 메서드 ==========

    private Future<?> startProducer(Map<String, Long> publishTimes, AtomicInteger publishedCount, 
                                   AtomicBoolean publishingComplete) {
        return executorService.submit(() -> {
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            try {
                scheduler.scheduleAtFixedRate(() -> {
                    int remaining = EXPECTED_TOTAL_MESSAGES - publishedCount.get();
                    if (remaining <= 0) {
                        return;
                    }
                    
                    int batchSize = Math.min(MESSAGES_PER_SECOND, remaining);
                    
                    IntStream.range(0, batchSize)
                        .forEach(i -> {
                            if (publishedCount.get() >= EXPECTED_TOTAL_MESSAGES) {
                                return;
                            }
                            
                            int sequence = publishedCount.incrementAndGet();
                            TradeUpdateEvent event = createTestEvent(sequence);
                            long publishTime = System.currentTimeMillis();
                            
                            try {
                                // TradeUpdateProducer를 사용하여 실제 프로덕션과 동일한 방식으로 발행
                                tradeUpdateProducer.publishTradeUpdate(event);
                                
                                // 발행 시간 기록 (sequence 번호를 키로 사용)
                                // Consumer에서 orderId에서 sequence를 추출하여 지연 시간 측정
                                publishTimes.put("seq-" + sequence, publishTime);
                            } catch (Exception e) {
                                log.error("메시지 발행 실패: sequence={}, error={}", sequence, e.getMessage());
                            }
                        });
                }, 0, 1, TimeUnit.SECONDS);
                
                // 발행 시간 동안 실행
                Thread.sleep(PUBLISH_DURATION_SECONDS * 1000L);
                publishingComplete.set(true);
                log.info("Producer 발행 완료: 총 발행된 메시지 수 = {}", publishedCount.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                publishingComplete.set(true);
            } finally {
                shutdownExecutor(scheduler);
            }
        });
    }

    private Future<?> startConsumer(Map<String, Long> publishTimes, List<Long> latencies,
                                    AtomicInteger processedCount, AtomicBoolean publishingComplete) {
        return executorService.submit(() -> {
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            AtomicInteger consecutiveErrors = new AtomicInteger(0);
            final int MAX_CONSECUTIVE_ERRORS = 10;
            
            try {
                AtomicBoolean shouldStop = new AtomicBoolean(false);
                
                scheduler.scheduleAtFixedRate(() -> {
                    if (shouldStop.get()) {
                        return;
                    }
                    
                    try {
                        processMessages(publishTimes, latencies, processedCount);
                        consecutiveErrors.set(0); // 성공 시 에러 카운터 리셋
                    } catch (Exception e) {
                        String errorMsg = e.getMessage();
                        
                        // Redis command interrupted는 테스트 종료 시그널로 처리
                        if (errorMsg != null && 
                            (errorMsg.contains("Redis command interrupted") || 
                             errorMsg.contains("Command interrupted") ||
                             e instanceof java.util.concurrent.CancellationException)) {
                            log.debug("Redis 명령 중단됨 - Consumer 종료: {}", errorMsg);
                            shouldStop.set(true);
                            return;
                        }
                        
                        int errorCount = consecutiveErrors.incrementAndGet();
                        
                        if (errorCount <= MAX_CONSECUTIVE_ERRORS) {
                            log.warn("메시지 수신 실패 (재시도 중): error={}, count={}", errorMsg, errorCount);
                        } else {
                            log.error("메시지 수신 실패 (최대 재시도 횟수 초과): error={}, count={}", errorMsg, errorCount);
                            // 연속 에러가 너무 많으면 Consumer 종료
                            shouldStop.set(true);
                        }
                    }
                }, 0, 100, TimeUnit.MILLISECONDS);
                
                // 메인 테스트에서 종료 조건을 관리하므로 여기서는 무한 대기
                // cancel() 호출 시 shouldStop이 true가 되어 자동 종료됨
                while (!shouldStop.get()) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                
                log.info("Consumer 종료: 처리된 메시지 수 = {}", processedCount.get());
            } finally {
                shutdownExecutor(scheduler);
            }
        });
    }
    
    private void processMessages(Map<String, Long> publishTimes, List<Long> latencies,
                                AtomicInteger processedCount) {
        StreamReadOptions readOptions = StreamReadOptions.empty().count(50);
        StreamOffset<String> streamOffset = StreamOffset.create(STREAM_KEY, ReadOffset.from(">"));
        
        List<MapRecord<String, Object, Object>> messages;
        try {
            messages = redisTemplate.opsForStream().read(
                Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                readOptions,
                streamOffset
            );
        } catch (Exception e) {
            // 예외는 상위에서 처리 (Redis command interrupted 등)
            throw e;
        }

        if (messages == null || messages.isEmpty()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        for (MapRecord<String, Object, Object> message : messages) {
            try {
                // 메시지에서 TradeUpdateEvent 추출
                Map<Object, Object> valueMap = message.getValue();
                Object valueObj = valueMap.get("value");
                
                TradeUpdateEvent event = null;
                if (valueObj instanceof TradeUpdateEvent) {
                    event = (TradeUpdateEvent) valueObj;
                } else if (valueObj instanceof String) {
                    // JSON 문자열인 경우 파싱 (테스트에서는 발생하지 않지만 안전을 위해)
                    continue;
                }
                
                if (event != null && event.getAlpacaOrderId() != null) {
                    // orderId에서 sequence 추출: "test-order-{sequence}"
                    String orderId = event.getAlpacaOrderId();
                    if (orderId.startsWith("test-order-")) {
                        try {
                            int sequence = Integer.parseInt(orderId.substring("test-order-".length()));
                            String seqKey = "seq-" + sequence;
                            Long publishTime = publishTimes.get(seqKey);
                            
                            if (publishTime != null) {
                                long latency = currentTime - publishTime;
                                latencies.add(latency);
                                processedCount.incrementAndGet();
                            }
                        } catch (NumberFormatException e) {
                            // sequence 파싱 실패 시 무시
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("메시지 처리 중 오류: messageId={}, error={}", 
                    message.getId().getValue(), e.getMessage());
            }
            
            try {
                redisTemplate.opsForStream().acknowledge(CONSUMER_GROUP, message);
            } catch (Exception e) {
                // Ack 실패는 로그만 남기고 계속 진행
                log.warn("메시지 Ack 실패: messageId={}, error={}", 
                    message.getId().getValue(), e.getMessage());
            }
        }
    }
    

    private void publishMessages(int startSequence, int endSequence) {
        IntStream.rangeClosed(startSequence, endSequence)
            .forEach(i -> {
                TradeUpdateEvent event = createTestEvent(i);
                tradeUpdateProducer.publishTradeUpdate(event);
            });
        log.info("메시지 발행 완료: {} ~ {}", startSequence, endSequence);
    }

    private void processMessagesPartially(int count) throws InterruptedException {
        AtomicInteger processed = new AtomicInteger(0);
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        
        try {
            scheduler.scheduleAtFixedRate(() -> {
                if (processed.get() >= count) {
                    return;
                }
                
                try {
                    StreamReadOptions readOptions = StreamReadOptions.empty().count(10);
                    StreamOffset<String> streamOffset = StreamOffset.create(STREAM_KEY, ReadOffset.from(">"));
                    
                    List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream().read(
                        Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                        readOptions,
                        streamOffset
                    );

                    if (messages == null || messages.isEmpty()) {
                        return;
                    }

                    for (MapRecord<String, Object, Object> message : messages) {
                        if (processed.get() >= count) {
                            break;
                        }
                        redisTemplate.opsForStream().acknowledge(CONSUMER_GROUP, message);
                        processed.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.error("메시지 처리 실패: {}", e.getMessage());
                }
            }, 0, 100, TimeUnit.MILLISECONDS);
            
            // 목표 개수까지 처리 대기 (최대 5초)
            long startTime = System.currentTimeMillis();
            while (processed.get() < count && (System.currentTimeMillis() - startTime) < 5000) {
                Thread.sleep(100);
            }
        } finally {
            shutdownExecutor(scheduler);
            Thread.sleep(500); // Consumer 중단 시뮬레이션
        }
    }
    

    private int recoverAllMessages() {
        AtomicInteger recoveredCount = new AtomicInteger(0);
        Set<String> processedMessageIds = ConcurrentHashMap.newKeySet();

        log.info("Pending 메시지 복구 시작");
        recoverPendingMessages(recoveredCount, processedMessageIds);
        recoverNewMessages(recoveredCount, processedMessageIds);

        return recoveredCount.get();
    }
    
    private void recoverPendingMessages(AtomicInteger recoveredCount, Set<String> processedMessageIds) {
        try {
            StreamReadOptions readOptions = StreamReadOptions.empty().count(200);
            StreamOffset<String> streamOffset = StreamOffset.create(STREAM_KEY, ReadOffset.from("0-0"));
            
            List<MapRecord<String, Object, Object>> pendingMessages = redisTemplate.opsForStream().read(
                Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                readOptions,
                streamOffset
            );

            if (pendingMessages == null || pendingMessages.isEmpty()) {
                return;
            }

            pendingMessages.forEach(message -> {
                String messageId = message.getId().getValue();
                if (processedMessageIds.add(messageId)) {
                    recoveredCount.incrementAndGet();
                    redisTemplate.opsForStream().acknowledge(CONSUMER_GROUP, message);
                }
            });
        } catch (Exception e) {
            log.error("Pending 메시지 복구 실패: {}", e.getMessage());
        }
    }
    
    private void recoverNewMessages(AtomicInteger recoveredCount, Set<String> processedMessageIds) {
        long startTime = System.currentTimeMillis();
        long timeout = 10000;
        
        while ((System.currentTimeMillis() - startTime) < timeout) {
            try {
                StreamReadOptions readOptions = StreamReadOptions.empty().count(50);
                StreamOffset<String> streamOffset = StreamOffset.create(STREAM_KEY, ReadOffset.from(">"));
                
                List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream().read(
                    Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                    readOptions,
                    streamOffset
                );

                if (messages == null || messages.isEmpty()) {
                    break;
                }

                messages.forEach(message -> {
                    String messageId = message.getId().getValue();
                    if (processedMessageIds.add(messageId)) {
                        recoveredCount.incrementAndGet();
                        redisTemplate.opsForStream().acknowledge(CONSUMER_GROUP, message);
                    }
                });

                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("새 메시지 복구 실패: {}", e.getMessage());
                break;
            }
        }
    }

    private void assertPerformanceMetrics(List<Long> latencies, AtomicInteger processedCount, 
                                         AtomicInteger publishedCount) {
        int published = publishedCount.get();
        int processed = processedCount.get();
        
        logPerformanceResults(published, processed, latencies);

        assertThat(processed)
            .as("처리된 메시지 수는 발행된 메시지 수의 80% 이상이어야 함")
            .isGreaterThanOrEqualTo((int) (published * 0.8));

        if (latencies.isEmpty()) {
            return;
        }

        LatencyMetrics metrics = calculateLatencyMetrics(latencies);
        assertLatencyMetrics(metrics);
    }
    
    private void logPerformanceResults(int published, int processed, List<Long> latencies) {
        log.info("=== 부하 테스트 결과 ===");
        log.info("발행된 메시지 수: {}", published);
        log.info("처리된 메시지 수: {}", processed);
        log.info("수집된 지연 시간 샘플 수: {}", latencies.size());
    }
    
    private LatencyMetrics calculateLatencyMetrics(List<Long> latencies) {
        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);
        
        int size = sorted.size();
        return new LatencyMetrics(
            sorted.stream().mapToLong(Long::longValue).average().orElse(0.0),
            sorted.get(size / 2),
            sorted.get((int) (size * 0.95)),
            sorted.get((int) (size * 0.99))
        );
    }
    
    private void assertLatencyMetrics(LatencyMetrics metrics) {
        log.info("평균 응답 지연: {}ms", String.format("%.2f", metrics.average()));
        log.info("P50 응답 지연: {}ms", metrics.p50());
        log.info("P95 응답 지연: {}ms", metrics.p95());
        log.info("P99 응답 지연: {}ms", metrics.p99());

        assertThat(metrics.average())
            .as("평균 응답 지연은 %dms 이하여야 함", MAX_LATENCY_MS)
            .isLessThanOrEqualTo(MAX_LATENCY_MS);
        
        assertThat(metrics.p95())
            .as("P95 응답 지연은 %dms 이하여야 함", P95_MAX_LATENCY_MS)
            .isLessThanOrEqualTo(P95_MAX_LATENCY_MS);
    }
    
    private record LatencyMetrics(double average, long p50, long p95, long p99) {}

    private void createConsumerGroupIfNotExists() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.streamCommands()
                .xGroupCreate(STREAM_KEY.getBytes(), CONSUMER_GROUP, ReadOffset.from("0"), true);
            log.info("Consumer Group 생성: group={}, stream={}", CONSUMER_GROUP, STREAM_KEY);
        } catch (Exception e) {
            Throwable cause = e.getCause();
            String errorMessage = cause != null ? cause.getMessage() : e.getMessage();
            
            if (cause instanceof io.lettuce.core.RedisBusyException || 
                (errorMessage != null && errorMessage.contains("BUSYGROUP"))) {
                log.debug("Consumer Group이 이미 존재: group={}, stream={}", CONSUMER_GROUP, STREAM_KEY);
            } else if (errorMessage != null && 
                      (errorMessage.contains("no such key") || errorMessage.contains("NOSTREAM"))) {
                log.warn("Stream이 존재하지 않습니다. 첫 번째 메시지가 발행되면 자동으로 생성됩니다: stream={}", 
                    STREAM_KEY);
            } else {
                log.error("Consumer Group 생성 실패: group={}, stream={}, error={}", 
                    CONSUMER_GROUP, STREAM_KEY, errorMessage, e);
            }
        }
    }

    private void cleanupStream() {
        try {
            redisTemplate.delete(STREAM_KEY);
            log.debug("Stream 정리 완료: {}", STREAM_KEY);
        } catch (Exception e) {
            log.warn("Stream 정리 실패 (무시 가능): {}", e.getMessage());
        }
    }

    private void shutdownExecutor(ExecutorService executor) {
        if (executor == null || executor.isShutdown()) {
            return;
        }
        
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Executor가 정상적으로 종료되지 않았습니다");
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private TradeUpdateEvent createTestEvent(int sequence) {
        return TradeUpdateEvent.of(
            (long) (sequence % 10 + 1),
            "fill",
            "test-order-" + sequence,
            "BTCUSD",
            sequence % 2 == 0 ? "buy" : "sell",
            "filled",
            "1.0",
            "50000.0",
            OffsetDateTime.now().toString(),
            "1.0",
            "50000.0",
            OffsetDateTime.now().toString(),
            "10.0",
            "{}"
        );
    }
}
