package com.example.liveChat.repositories;

import com.example.liveChat.models.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    @Query("select user from User user where user.id = :id and user.deletedAt is null")
    Optional<User> findActiveById(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.id = :id and user.deletedAt is null")
    Optional<User> findActiveByIdForUpdate(@Param("id") String id);

    @Query("select user from User user where lower(user.email) = lower(:email) and user.deletedAt is null")
    Optional<User> findActiveByEmailIgnoreCase(@Param("email") String email);

    @Query("select user from User user where user.deletedAt is null")
    Page<User> findAllActive(Pageable pageable);
}
