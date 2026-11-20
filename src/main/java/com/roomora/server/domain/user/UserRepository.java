package com.roomora.server.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmailAndProviderAndDeletedAtIsNull(String email, AuthProvider provider);

    boolean existsByNicknameAndDeletedAtIsNull(String nickname);

    Optional<User> findByEmailAndProviderAndDeletedAtIsNull(String email, AuthProvider provider);

    Optional<User> findByProviderAndProviderIdAndDeletedAtIsNull(AuthProvider provider, String providerId);

    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.followerCount = u.followerCount + 1 WHERE u.id = :id")
    void incrementFollowerCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.followerCount = CASE WHEN u.followerCount > 0 THEN u.followerCount - 1 ELSE 0 END WHERE u.id = :id")
    void decrementFollowerCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.followingCount = u.followingCount + 1 WHERE u.id = :id")
    void incrementFollowingCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.followingCount = CASE WHEN u.followingCount > 0 THEN u.followingCount - 1 ELSE 0 END WHERE u.id = :id")
    void decrementFollowingCount(@Param("id") Long id);
}
