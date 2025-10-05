package com.curihous.qbit.api.domain.user.service;

import com.curihous.qbit.api.config.security.CustomUserDetails;
import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.api.domain.auth.dto.OAuth2UserDetails;
import com.curihous.qbit.domain.user.entity.User;
import com.curihous.qbit.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 파사드 서비스 (Application Layer)
 * Security Context 처리 및 Domain Service 오케스트레이션
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserFacadeService {

    private final UserService userService;

    // 현재 로그인한 사용자 정보 반환
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new QbitException(ErrorCode.UNAUTHORIZED);
        }

        // OAuth2UserDetails에서 userId 가져오기 (OAuth2 로그인 직후)
        if (authentication.getPrincipal() instanceof OAuth2UserDetails userDetails) {
            return userService.findUserById(userDetails.userId());
        }

        // JWT 토큰 기반 인증 요청인 경우 - CustomUserDetails에서 userId 추출
        if (authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userService.findUserById(userDetails.getUserId());
        }

        throw new QbitException(ErrorCode.UNAUTHORIZED);
    }

    @Transactional
    public void deleteCurrentUser() {
        User user = getCurrentUser();
        userService.deleteUser(user.getUserId());
    }

}

