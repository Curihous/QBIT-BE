package com.curihous.qbit.domain.user.service;

import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.domain.user.entity.User;
import com.curihous.qbit.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 도메인 서비스
 * 순수 비즈니스 로직만 포함 (Security, DTO 의존 X)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new QbitException(ErrorCode.USER_NOT_FOUND));
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new QbitException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = findById(userId);
        user.deactivate();
    }

    @Transactional
    public void deactivateUser(Long userId) {
        User user = findById(userId);
        user.deactivate();
    }

    @Transactional
    public void activateUser(Long userId) {
        User user = findById(userId);
        user.activate();
    }
}

