package com.curihous.qbit.api.domain.user.dto;

import com.curihous.qbit.domain.user.entity.LoginType;
import com.curihous.qbit.domain.user.entity.User;

import java.time.LocalDateTime;

// TODO: 확인용 DTO이므로 제거 또는 수정 필요
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
                user.getUserId(),
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