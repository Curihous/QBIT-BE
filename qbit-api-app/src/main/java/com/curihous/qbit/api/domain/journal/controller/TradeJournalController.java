package com.curihous.qbit.api.domain.journal.controller;

import com.curihous.qbit.api.domain.journal.dto.request.TradeJournalRequestDto;
import com.curihous.qbit.api.domain.journal.dto.response.TradeJournalResponseDto;
import com.curihous.qbit.common.dto.PaginatedResponseDto;
import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.common.util.PagingValidator;
import com.curihous.qbit.domain.journal.entity.TradeEmotion;
import com.curihous.qbit.domain.journal.entity.TradeJournal;
import com.curihous.qbit.domain.journal.service.TradeJournalService;
import com.curihous.qbit.domain.order.entity.OrderSide;
import com.curihous.qbit.domain.user.entity.User;
import com.curihous.qbit.infra.security.facade.UserSecurityFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/journals")
@RequiredArgsConstructor
@Tag(name = "Journal", description = "거래 일지 관련 API")
public class TradeJournalController {

    private final TradeJournalService tradeJournalService;
    private final UserSecurityFacade userSecurityFacade;

    @Operation(summary = "거래 일지 작성", description = "체결된 주문에 대해 거래 일지를 작성합니다.")
    @PostMapping("/orders/{orderId}")
    public ResponseEntity<TradeJournalResponseDto> createTradeJournal(
        @PathVariable Long orderId,
        @Valid @RequestBody TradeJournalRequestDto request
    ) {
        User user = userSecurityFacade.getCurrentUser();
        TradeEmotion tradeEmotion = resolveTradeEmotion(request.tradeEmotion());
        TradeJournal journal = tradeJournalService.createTradeJournal(user, orderId, request.content(), tradeEmotion);
        return ResponseEntity.ok(TradeJournalResponseDto.from(journal));
    }

    @Operation(summary = "거래 일지 월별 조회", description = "특정 연도와 월에 작성된 거래 일지를 최신순으로 조회합니다.")
    @GetMapping("/monthly")
    public ResponseEntity<PaginatedResponseDto<TradeJournalResponseDto>> getTradeJournalsByMonth(
        @RequestParam int year,
        @RequestParam int month,
        @RequestParam(required = false) OrderSide side,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        PagingValidator.validate(page, size);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        User user = userSecurityFacade.getCurrentUser();
        Page<TradeJournal> journalPage = tradeJournalService.getTradeJournalsByMonth(
            user,
            year,
            month,
            side,
            pageable
        );

        Page<TradeJournalResponseDto> mapped = journalPage.map(TradeJournalResponseDto::from);
        return ResponseEntity.ok(PaginatedResponseDto.from(mapped));
    }

    @Operation(summary = "거래 일지 상세 조회", description = "거래 일지 상세 정보를 조회합니다.")
    @GetMapping("/{journalId}")
    public ResponseEntity<TradeJournalResponseDto> getTradeJournal(@PathVariable Long journalId) {
        User user = userSecurityFacade.getCurrentUser();
        TradeJournal journal = tradeJournalService.getTradeJournal(journalId, user);
        return ResponseEntity.ok(TradeJournalResponseDto.from(journal));
    }

    @Operation(summary = "거래 일지 수정", description = "거래 일지 내용을 수정합니다.")
    @PutMapping("/{journalId}")
    public ResponseEntity<TradeJournalResponseDto> updateTradeJournal(
        @PathVariable Long journalId,
        @Valid @RequestBody TradeJournalRequestDto request
    ) {
        User user = userSecurityFacade.getCurrentUser();
        TradeEmotion tradeEmotion = resolveTradeEmotion(request.tradeEmotion());
        TradeJournal journal = tradeJournalService.updateTradeJournal(journalId, user, request.content(), tradeEmotion);
        return ResponseEntity.ok(TradeJournalResponseDto.from(journal));
    }

    @Operation(summary = "거래 일지 삭제", description = "거래 일지를 삭제합니다.")
    @DeleteMapping("/{journalId}")
    public ResponseEntity<Void> deleteTradeJournal(@PathVariable Long journalId) {
        User user = userSecurityFacade.getCurrentUser();
        tradeJournalService.deleteTradeJournal(journalId, user);
        return ResponseEntity.noContent().build();
    }

    private TradeEmotion resolveTradeEmotion(String tradeEmotion) {
        if (tradeEmotion == null || tradeEmotion.isBlank()) {
            return null;
        }
        try {
            return TradeEmotion.valueOf(tradeEmotion.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new QbitException(ErrorCode.INVALID_INPUT_VALUE, "허용된 tradeEmotion 값이 아닙니다.");
        }
    }
}

