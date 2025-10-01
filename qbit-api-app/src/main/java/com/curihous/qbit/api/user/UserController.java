package com.curihous.qbit.api.user;

import com.curihous.qbit.domain.user.dto.UserResponseDto;
import com.curihous.qbit.domain.user.entity.User;
import com.curihous.qbit.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// 사용자 관련 API
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // 현재 로그인한 사용자 정보 조회
    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> getCurrentUser() {
        User user = userService.getCurrentUser();
        return ResponseEntity.ok(UserResponseDto.from(user));
    }

    // 사용자 비활성화 (탈퇴)
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteCurrentUser() {
        userService.deleteCurrentUser();
        return ResponseEntity.ok().build();
    }
}
