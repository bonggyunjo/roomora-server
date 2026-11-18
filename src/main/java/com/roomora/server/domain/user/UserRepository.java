package com.roomora.server.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmailAndProviderAndDeletedAtIsNull(String email, AuthProvider provider);

    boolean existsByNicknameAndDeletedAtIsNull(String nickname);

    Optional<User> findByEmailAndProviderAndDeletedAtIsNull(String email, AuthProvider provider);

    Optional<User> findByProviderAndProviderIdAndDeletedAtIsNull(AuthProvider provider, String providerId);

    Optional<User> findByIdAndDeletedAtIsNull(Long id);
}
