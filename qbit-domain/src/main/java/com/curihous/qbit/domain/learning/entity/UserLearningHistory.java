package com.curihous.qbit.domain.learning.entity;

import com.curihous.qbit.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// TODO: 기획에 따른 엔티티 구조 수정
@Entity
@Table(name = "user_learning_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserLearningHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_learning_history_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public UserLearningHistory(User user) {
        this.user = user;
    }
}
