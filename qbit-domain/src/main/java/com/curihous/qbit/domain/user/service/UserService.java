package com.curihous.qbit.domain.user.service;

import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.domain.auth.dto.OAuth2UserDetails;
import com.curihous.qbit.domain.user.dto.UserResponseDto;
import com.curihous.qbit.domain.user.entity.User;
import com.curihous.qbit.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new QbitException(ErrorCode.USER_NOT_FOUND));
    }

    // 현재 로그인한 사용자 정보 반환
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new QbitException(ErrorCode.UNAUTHORIZED);
        }
        
        // OAuth2UserDetails에서 userId 가져오기(카카오 로그인 직후후)
        if (authentication.getPrincipal() instanceof OAuth2UserDetails userDetails) {
            return findUserById(userDetails.userId());
        }
        
        // JWT 토큰 기반 인증 요청인 경우
        if (authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.User) {
            String email = authentication.getName();
            return userRepository.findByEmail(email)
                    .orElseThrow(() -> new QbitException(ErrorCode.USER_NOT_FOUND));
        }
        
        throw new QbitException(ErrorCode.UNAUTHORIZED);
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = findUserById(userId);
        user.deactivate();
    }

    @Transactional
    public void deleteCurrentUser() {
        User user = getCurrentUser();
        user.deactivate();
    }

    @Transactional
    public void deactivateUser(Long userId) {
        User user = findUserById(userId);
        user.deactivate();
    }

    @Transactional
    public void activateUser(Long userId) {
        User user = findUserById(userId);
        user.activate();
    }

}