package com.curihous.qbit.infra.security.facade;

import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.domain.user.entity.User;
import com.curihous.qbit.domain.user.service.UserService;
import com.curihous.qbit.infra.security.util.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 파사드 서비스 (Application Layer)
 * Security Context 처리 및 Domain Service 오케스트레이션
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserSecurityFacade {

    private final UserService userService;

    // 현재 로그인한 사용자 정보 반환
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new QbitException(ErrorCode.UNAUTHORIZED);
        }

        // JWT 토큰 기반 인증 요청 - CustomUserDetails에서 userId 추출
        if (authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userService.findById(userDetails.getUserId());
        }

        throw new QbitException(ErrorCode.UNAUTHORIZED);
    }

    @Transactional
    public void deleteCurrentUser() {
        User user = getCurrentUser();
        userService.deleteUser(user.getId());
    }
}
