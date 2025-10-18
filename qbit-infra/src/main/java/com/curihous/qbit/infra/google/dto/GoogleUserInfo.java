package com.curihous.qbit.infra.google.dto;

/**
 * 구글 사용자 정보 DTO
 * 
 * - 구글 ID 토큰 검증 후 사용자 생성 및 조회
 */
public record GoogleUserInfo(
    String id,
    String email,
    String name,
    String picture
) {
    public String getNickname() {
        return name;
    }
}

