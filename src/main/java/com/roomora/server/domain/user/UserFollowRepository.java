package com.roomora.server.domain.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserFollowRepository extends JpaRepository<UserFollow, Long> {

    Optional<UserFollow> findByFollowerIdAndFollowingId(Long followerId, Long followingId);

    boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);

    @Query("SELECT f FROM UserFollow f JOIN FETCH f.follower u " +
           "WHERE f.following.id = :targetUserId AND u.deletedAt IS NULL " +
           "ORDER BY f.createdAt DESC")
    Page<UserFollow> findFollowers(@Param("targetUserId") Long targetUserId, Pageable pageable);

    @Query("SELECT f FROM UserFollow f JOIN FETCH f.following u " +
           "WHERE f.follower.id = :targetUserId AND u.deletedAt IS NULL " +
           "ORDER BY f.createdAt DESC")
    Page<UserFollow> findFollowings(@Param("targetUserId") Long targetUserId, Pageable pageable);
}
