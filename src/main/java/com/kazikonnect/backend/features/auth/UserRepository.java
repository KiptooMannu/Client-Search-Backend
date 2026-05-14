package com.kazikonnect.backend.features.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    java.util.List<User> findAllByRole(UserRole role);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("UPDATE User u SET u.fullName = CONCAT(u.firstName, ' ', u.secondName) " +
           "WHERE (u.fullName IS NULL OR u.fullName = 'Unknown' OR u.fullName = '') " +
           "AND u.firstName IS NOT NULL AND u.firstName != '' " +
           "AND u.secondName IS NOT NULL AND u.secondName != ''")
    int updateFullNamesFromFirstAndSecond();

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("UPDATE User u SET u.fullName = u.firstName " +
           "WHERE (u.fullName IS NULL OR u.fullName = 'Unknown' OR u.fullName = '') " +
           "AND u.firstName IS NOT NULL AND u.firstName != '' " +
           "AND (u.secondName IS NULL OR u.secondName = '')")
    int updateFullNamesFromFirstOnly();

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("UPDATE User u SET u.fullName = u.username " +
           "WHERE (u.fullName IS NULL OR u.fullName = 'Unknown' OR u.fullName = '') " +
           "AND (u.firstName IS NULL OR u.firstName = '')")
    int updateFullNamesFromUsername();
}
