package com.curihous.qbit.api.domain.user.dto;

import com.curihous.qbit.domain.user.entity.LoginType;
import com.curihous.qbit.domain.user.entity.User;

import java.time.LocalDateTime;

// TODO: 확인용 DTO이므로 제거 또는 수정 필요
public record UserResponseDto(
    Long userId,
    String email,
    String nickname,
    String userName,
    String provider,
    LoginType loginType,
    Boolean isActive,
    Boolean isNotificationEnabled,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static UserResponseDto from(User user) {
        return new UserResponseDto(
                user.getUserId(),
                user.getEmail(),
                user.getNickname(),
                user.getUserName(),
                user.getProvider(),
                user.getLoginType(),
                user.getIsActive(),
                user.getIsNotificationEnabled(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}