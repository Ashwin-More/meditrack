package com.meditrack.repository;

import com.meditrack.entity.User;
import com.meditrack.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
    List<User> findByStatus(UserStatus status);
    List<User> findByRole_RoleName(com.meditrack.enums.RoleName roleName);
    long countByRole_RoleNameAndStatus(com.meditrack.enums.RoleName roleName, UserStatus status);
}
