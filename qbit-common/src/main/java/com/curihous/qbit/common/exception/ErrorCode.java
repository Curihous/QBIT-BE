package com.curihous.qbit.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    // 도메인
    // 타입(상태 코드, "메시지");

    // Default
    ERROR(400, "요청 처리에 실패했습니다."),

    // 400 Bad Request
    // 입력 에러
    INVALID_INPUT_FORMAT(400, "유효하지 않은 형식입니다."),
    INVALID_INPUT_LENGTH(400, "입력 길이가 잘못되었습니다."),
    INVALID_INPUT_VALUE(400, "입력값이 잘못되었습니다."),
    MISSING_PARAMETER(400, "필수 파라미터가 누락되었습니다."),
    INVALID_ENUM_VALUE(400, "enum 값이 잘못되었습니다."),

    // 주식 관련 에러
    INVALID_STOCK_TICKER(400, "유효하지 않은 종목코드입니다."),
    INVALID_ORDER_TYPE(400, "유효하지 않은 주문 유형입니다."),
    INVALID_ORDER_QUANTITY(400, "유효하지 않은 주문 수량입니다."),
    INVALID_ORDER_PRICE(400, "유효하지 않은 주문 가격입니다."),
    INSUFFICIENT_BALANCE(400, "잔고가 부족합니다."),
    INVALID_TRADE_CYCLE(400, "유효하지 않은 거래 사이클입니다."),

    // 401 Unauthorized
    // 로그인 상태여야 하는 요청
    NOT_AUTHENTICATED(401, "로그인 상태가 아닙니다."),
    UNAUTHORIZED(401, "인증이 필요합니다."),
    // 소셜 로그인이 정상적으로 이루어지지 않음
    OAUTH2_LOGIN_FAILED(401, "소셜 로그인에 실패했습니다."),
    EMAIL_ALREADY_REGISTERED(401, "이미 가입된 이메일입니다."),
    ILLEGAL_REGISTRATION_ID(401, "지원하지 않는 로그인 타입입니다."),
    // 유효하지 않은 토큰
    INVALID_TOKEN(401, "유효하지 않은 토큰입니다."),
    INVALID_REFRESH_TOKEN(401, "유효하지 않은 리프레시 토큰입니다."),
    // 만료된 토큰
    EXPIRED_ACCESS_TOKEN(401, "만료된 액세스 토큰입니다."),
    EXPIRED_REFRESH_TOKEN(401, "만료된 리프레시 토큰입니다."),
    // 액세스 토큰이 만료되지 않은 상황에서 재발급받으려는 경우
    ACCESS_TOKEN_NOT_EXPIRED(401, "액세스 토큰이 아직 만료되지 않았습니다."),
    // 쿠키에 리프레시 토큰이 들어있지 않은 경우
    NO_COOKIE(401, "쿠키에 리프레시 토큰이 존재하지 않습니다."),
    USER_STATUS_IS_NOT_ACTIVE(401, "계정이 활성 상태가 아닙니다."),

    // 403 Forbidden
    // 권한이 없는 요청을 보냄
    UNAUTHORIZED_REQUEST(403, "권한이 없습니다."),
    PORTFOLIO_ACCESS_DENIED(403, "포트폴리오 접근 권한이 없습니다."),
    TRADE_HISTORY_ACCESS_DENIED(403, "거래 내역 접근 권한이 없습니다."),

    // 404 Not Found
    // 각 리소스를 찾지 못함
    USER_NOT_FOUND(404, "사용자를 찾을 수 없습니다."),
    STOCK_NOT_FOUND(404, "주식을 찾을 수 없습니다."),
    PORTFOLIO_NOT_FOUND(404, "포트폴리오를 찾을 수 없습니다."),
    TRADE_CYCLE_NOT_FOUND(404, "거래 사이클을 찾을 수 없습니다."),
    ORDER_REQUEST_NOT_FOUND(404, "주문 요청을 찾을 수 없습니다."),
    TRADE_EXECUTION_NOT_FOUND(404, "거래 체결을 찾을 수 없습니다."),
    DAILY_ASSET_NOT_FOUND(404, "일별 자산을 찾을 수 없습니다."),
    JOURNAL_NOT_FOUND(404, "매매 일지를 찾을 수 없습니다."),
    TRADE_REPORT_NOT_FOUND(404, "거래 리포트를 찾을 수 없습니다."),
    USER_LEARNING_HISTORY_NOT_FOUND(404, "사용자 학습 기록을 찾을 수 없습니다."),

    // 409 Conflict
    // 중복 리소스 생성 시도
    USER_ALREADY_EXISTS(409, "이미 존재하는 사용자입니다."),
    EMAIL_ALREADY_EXISTS(409, "이미 존재하는 이메일입니다."),
    NICKNAME_ALREADY_EXISTS(409, "이미 존재하는 닉네임입니다."),
    STOCK_ALREADY_EXISTS(409, "이미 존재하는 주식입니다."),
    PORTFOLIO_ALREADY_EXISTS(409, "이미 존재하는 포트폴리오입니다."),
    TRADE_CYCLE_ALREADY_EXISTS(409, "이미 존재하는 거래 사이클입니다."),
    ORDER_REQUEST_ALREADY_EXISTS(409, "이미 존재하는 주문 요청입니다."),
    JOURNAL_ALREADY_EXISTS(409, "이미 존재하는 매매 일지입니다."),
    TRADE_REPORT_ALREADY_EXISTS(409, "이미 존재하는 거래 리포트입니다."),

    // 500 Internal Server Error
    // 외부 API 사용 도중 에러
    REDIS_ERROR(500, "서버에서 Redis 사용 중 문제가 발생했습니다."),
    EXTERNAL_API_ERROR(500, "외부 API 사용 중 문제가 발생했습니다."),
    STOCK_API_ERROR(500, "주식 API 사용 중 문제가 발생했습니다."),
    PAYMENT_API_ERROR(500, "결제 API 사용 중 문제가 발생했습니다."),
    NOTIFICATION_API_ERROR(500, "알림 API 사용 중 문제가 발생했습니다."),
    DATABASE_ERROR(500, "데이터베이스 오류가 발생했습니다."),
    INTERNAL_SERVER_ERROR(500, "서버 내부 오류가 발생했습니다."),
    WEBSOCKET_CONNECTION_ERROR(500, "WebSocket 연결 중 문제가 발생했습니다."),
    
    // Auth 관련 내부 에러
    COOKIE_ADD_FAILED(500, "쿠키 추가에 실패했습니다."),
    COOKIE_DELETE_FAILED(500, "쿠키 삭제에 실패했습니다."),
    COOKIE_GET_FAILED(500, "쿠키 조회에 실패했습니다."),
    JWT_GENERATION_FAILED(500, "JWT 토큰 생성에 실패했습니다."),
    JWT_VALIDATION_FAILED(500, "JWT 토큰 검증에 실패했습니다."),

    ;

    private final int status;
    private final String message;
}
