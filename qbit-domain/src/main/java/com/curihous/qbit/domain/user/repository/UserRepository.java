package com.curihous.qbit.domain.user.repository;

import com.curihous.qbit.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
