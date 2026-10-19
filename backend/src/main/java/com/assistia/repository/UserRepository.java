package com.assistia.repository;

import com.assistia.entity.User;
import com.assistia.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByRoleAndPasswordHashIsNotNullAndEmailNotIgnoreCase(UserRole role, String email);
}
