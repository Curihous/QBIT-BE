package com.curihous.qbit.domain.user.entity;

import com.curihous.qbit.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Email
    @NotBlank
    @Column(unique = true, nullable = false)
    private String email;

    @NotBlank
    @Column(unique = true, nullable = false, length = 10)
    private String nickname;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(nullable = false)
    private LoginType loginType;

    // 간단한 상태 관리
    // TODO: 기획에 따른 상태 관리 수정
    @Column(nullable = false)
    private boolean isActive = true;

    // 알림 설정
    @Column(nullable = false)
    private boolean isNotificationEnabled = true;

    @Builder
    public User(String email, String nickname, String provider,
                LoginType loginType) {
        this.email = email;
        this.nickname = nickname;
        this.provider = provider;
        this.loginType = loginType;
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }
}