package com.curihous.qbit.api.domain.user.controller;

import com.curihous.qbit.api.domain.user.dto.response.UserResponseDto;
import com.curihous.qbit.domain.user.entity.User;
import com.curihous.qbit.infra.security.facade.UserSecurityFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User", description = "User 관련 API입니다.")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserSecurityFacade userSecurityFacade;

    @Operation(summary = "현재 사용자 정보 조회", description = "현재 로그인한 사용자의 정보를 조회합니다.")
    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> getCurrentUser() {
        User user = userSecurityFacade.getCurrentUser();
        return ResponseEntity.ok(UserResponseDto.from(user));
    }

    @Operation(summary = "사용자 탈퇴", description = "현재 로그인한 사용자를 탈퇴 처리합니다.")
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteCurrentUser() {
        userSecurityFacade.deleteCurrentUser();
        return ResponseEntity.ok().build();
    }
}
