package com.curihous.qbit.domain.journal.service;

import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.domain.journal.entity.TradeEmotion;
import com.curihous.qbit.domain.journal.entity.TradeJournal;
import com.curihous.qbit.domain.journal.repository.TradeJournalRepository;
import com.curihous.qbit.domain.order.entity.OrderRequest;
import com.curihous.qbit.domain.order.entity.OrderSide;
import com.curihous.qbit.domain.order.repository.OrderRequestRepository;
import com.curihous.qbit.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradeJournalService {

    private final TradeJournalRepository tradeJournalRepository;
    private final OrderRequestRepository orderRequestRepository;

    // 매매 일지 생성
    @Transactional
    public TradeJournal createTradeJournal(User user, Long orderId, String content, TradeEmotion tradeEmotion) {
        OrderRequest orderRequest = orderRequestRepository.findByIdAndUser(orderId, user)
            .orElseThrow(() -> new QbitException(ErrorCode.ORDER_REQUEST_NOT_FOUND,
                "주문을 찾을 수 없습니다. orderId=" + orderId));

        validateOrderRequest(user, orderRequest);

        tradeJournalRepository.findByUserAndOrderRequest(user, orderRequest).ifPresent(journal -> {
            throw new QbitException(ErrorCode.JOURNAL_ALREADY_EXISTS,
                "해당 주문에 대한 매매 일지가 이미 존재합니다. orderRequestId=" + orderId);
        });

        TradeJournal journal = new TradeJournal(content, tradeEmotion != null ? tradeEmotion : TradeEmotion.NEUTRAL, user, orderRequest);
        return tradeJournalRepository.save(journal);
    }

    // 매매 일지 월별 조회(페이징, 필터 - 전체, 매수, 매도)
    public Page<TradeJournal> getTradeJournalsByMonth(User user, int year, int month, OrderSide side, Pageable pageable) {
        YearMonth yearMonth;
        try {
            yearMonth = YearMonth.of(year, month);
        } catch (DateTimeException e) {
            throw new QbitException(ErrorCode.INVALID_INPUT_VALUE, "유효하지 않은 연도 또는 월입니다.");
        }
        LocalDateTime start = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime end = yearMonth.plusMonths(1).atDay(1).atStartOfDay();

        if (side == null) {
            return tradeJournalRepository.findByUserAndCreatedAtBetween(user, start, end, pageable);
        }

        return tradeJournalRepository.findByUserAndOrderRequest_SideAndCreatedAtBetween(user, side, start, end, pageable);
    }

    // 매매 일지 조회
    public TradeJournal getTradeJournal(Long journalId, User user) {
        return tradeJournalRepository.findById(journalId)
            .filter(journal -> journal.getUser().getId().equals(user.getId()))
            .orElseThrow(() -> new QbitException(ErrorCode.JOURNAL_NOT_FOUND,
                "매매 일지를 찾을 수 없습니다. journalId=" + journalId));
    }

    // 매매 일지 수정
    @Transactional
    public TradeJournal updateTradeJournal(Long journalId, User user, String content, TradeEmotion tradeEmotion) {
        TradeJournal journal = getTradeJournal(journalId, user);
        journal.update(content, tradeEmotion != null ? tradeEmotion : TradeEmotion.NEUTRAL);
        return journal;
    }

    // 매매 일지 삭제
    @Transactional
    public void deleteTradeJournal(Long journalId, User user) {
        TradeJournal journal = getTradeJournal(journalId, user);
        tradeJournalRepository.delete(journal);
    }

    
    // ======= 헬퍼 메서드 =======

    // 주문 요청 유효성 검사
    private void validateOrderRequest(User user, OrderRequest orderRequest) {
        if (!orderRequest.getUser().getId().equals(user.getId())) {
            throw new QbitException(ErrorCode.ORDER_REQUEST_ACCESS_DENIED,
                "해당 주문에 접근할 수 없습니다. orderRequestId=" + orderRequest.getId());
        }
    }
}

