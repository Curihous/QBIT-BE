package com.curihous.qbit.api.domain.user.dto.response;

import com.curihous.qbit.domain.user.entity.LoginType;
import com.curihous.qbit.domain.user.entity.User;

import java.time.LocalDateTime;

/**
 * 사용자 정보 조회 응답 DTO
 * 
 * 사용 API:
 * - GET /users/me
 */
public record UserResponseDto(
    Long userId,
    String email,
    String nickname,
    String provider,
    LoginType loginType,
    boolean isActive,
    boolean isNotificationEnabled,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static UserResponseDto from(User user) {
        return new UserResponseDto(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProvider(),
                user.getLoginType(),
                user.isActive(),
                user.isNotificationEnabled(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}

