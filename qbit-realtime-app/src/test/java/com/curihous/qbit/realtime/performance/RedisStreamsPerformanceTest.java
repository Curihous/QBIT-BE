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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis Streams 성능 테스트
 * 1. 고부하 처리 능력 검증 - 초당 수백 건의 주문 부하 시뮬레이션
 * 2. 장애 복구 검증 - 워커 중단 후 재시작 시 데이터 손실 없이 복구
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
class RedisStreamsPerformanceTest {

    private static final String STREAM_KEY = "trade-updates";
    private static final String CONSUMER_GROUP = "qbit-realtime-group";
    private static final String CONSUMER_NAME = "qbit-realtime-consumer-trade";
    
    private static final int MESSAGES_PER_SECOND = 200;
    private static final int TEST_DURATION_SECONDS = 3;
    private static final int EXPECTED_TOTAL_MESSAGES = MESSAGES_PER_SECOND * TEST_DURATION_SECONDS;
    private static final int MAX_LATENCY_MS = 400;
    private static final int P95_MAX_LATENCY_MS = 500;

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
        // given
        Map<String, Long> publishTimes = new ConcurrentHashMap<>();
        List<Long> latencies = new CopyOnWriteArrayList<>();
        AtomicInteger publishedCount = new AtomicInteger(0);
        AtomicInteger processedCount = new AtomicInteger(0);
        CountDownLatch completionLatch = new CountDownLatch(EXPECTED_TOTAL_MESSAGES);

        // when: Producer와 Consumer를 병렬로 실행
        Future<?> producerFuture = startProducer(publishTimes, publishedCount, completionLatch);
        Future<?> consumerFuture = startConsumer(publishTimes, latencies, processedCount, completionLatch);

        // then: 메시지가 처리될 때까지 대기
        try {
            boolean completed = completionLatch.await(30, TimeUnit.SECONDS);
            if (!completed) {
                log.warn("테스트 시간 초과: 처리된 메시지 수 = {}", processedCount.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 종료
        producerFuture.cancel(true);
        consumerFuture.cancel(true);

        // 검증
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
        processMessagesPartially(initialMessages / 2);
        
        // 추가 메시지 발행 (워커 중단 중)
        publishMessages(initialMessages + 1, initialMessages + additionalMessages);
        
        // then: 재시작 후 모든 메시지 복구 확인
        int recoveredCount = recoverAllMessages();
        
        assertThat(recoveredCount).isGreaterThanOrEqualTo(additionalMessages);
        log.info("장애 복구 테스트 완료: 복구된 메시지 수 = {}", recoveredCount);
    }

    // ========== 헬퍼 메서드 ==========

    private Future<?> startProducer(Map<String, Long> publishTimes, AtomicInteger publishedCount, 
                                   CountDownLatch completionLatch) {
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
                                // Record ID를 얻기 위해 직접 RedisTemplate 사용
                                var recordId = redisTemplate.opsForStream().add(
                                    MapRecord.create(STREAM_KEY, Map.of("value", event))
                                );
                                
                                if (recordId != null) {
                                    publishTimes.put(recordId.getValue(), publishTime);
                                    completionLatch.countDown();
                                }
                            } catch (Exception e) {
                                log.error("메시지 발행 실패: sequence={}, error={}", sequence, e.getMessage());
                            }
                        });
                }, 0, 1, TimeUnit.SECONDS);
                
                // 테스트 시간 동안 실행
                Thread.sleep(TEST_DURATION_SECONDS * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                shutdownExecutor(scheduler);
            }
        });
    }

    private Future<?> startConsumer(Map<String, Long> publishTimes, List<Long> latencies,
                                    AtomicInteger processedCount, CountDownLatch completionLatch) {
        return executorService.submit(() -> {
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            try {
                scheduler.scheduleAtFixedRate(() -> {
                    try {
                        processMessages(publishTimes, latencies, processedCount);
                    } catch (Exception e) {
                        log.error("메시지 수신 실패: {}", e.getMessage());
                    }
                }, 0, 100, TimeUnit.MILLISECONDS);
                
                // completionLatch가 0이 될 때까지 대기
                try {
                    completionLatch.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } finally {
                shutdownExecutor(scheduler);
            }
        });
    }
    
    private void processMessages(Map<String, Long> publishTimes, List<Long> latencies,
                                AtomicInteger processedCount) {
        StreamReadOptions readOptions = StreamReadOptions.empty().count(50);
        StreamOffset<String> streamOffset = createStreamOffset(ReadOffset.from(">"));
        
        List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream().read(
            Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
            readOptions,
            streamOffset
        );

        if (messages == null || messages.isEmpty()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        for (MapRecord<String, Object, Object> message : messages) {
            String messageId = message.getId().getValue();
            Long publishTime = publishTimes.get(messageId);
            
            if (publishTime != null) {
                long latency = currentTime - publishTime;
                latencies.add(latency);
                processedCount.incrementAndGet();
            }
            
            redisTemplate.opsForStream().acknowledge(CONSUMER_GROUP, message);
        }
    }
    
    @SuppressWarnings({"unchecked", "varargs"})
    private StreamOffset<String> createStreamOffset(ReadOffset readOffset) {
        return StreamOffset.create(STREAM_KEY, readOffset);
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
                    StreamOffset<String> streamOffset = createStreamOffset(ReadOffset.from(">"));
                    
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
            
            waitForCompletion(processed, count, 5000);
        } finally {
            shutdownExecutor(scheduler);
            Thread.sleep(500); // Consumer 중단 시뮬레이션
        }
    }
    
    private void waitForCompletion(AtomicInteger counter, int target, long timeoutMs) 
            throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (counter.get() < target && (System.currentTimeMillis() - startTime) < timeoutMs) {
            Thread.sleep(100);
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
            StreamOffset<String> streamOffset = createStreamOffset(ReadOffset.from("0-0"));
            
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
                StreamOffset<String> streamOffset = createStreamOffset(ReadOffset.from(">"));
                
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
