package com.curihous.qbit.domain.user.entity;

import com.curihous.qbit.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "nickname", nullable = false)
    private String nickname;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "provider", nullable = false)
    private String provider;

    public User(String email, String password, String nickname, String userName, String provider) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.userName = userName;
        this.provider = provider;
    }
}
