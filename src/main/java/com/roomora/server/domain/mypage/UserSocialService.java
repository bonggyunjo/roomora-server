package com.roomora.server.domain.mypage;

import com.roomora.server.domain.auth.RefreshTokenRedisRepository;
import com.roomora.server.domain.mypage.dto.SocialDtos.*;
import com.roomora.server.domain.user.User;
import com.roomora.server.domain.user.UserFollow;
import com.roomora.server.domain.user.UserFollowRepository;
import com.roomora.server.domain.user.UserRepository;
import com.roomora.server.global.exception.BusinessException;
import com.roomora.server.global.exception.ErrorCode;
import com.roomora.server.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserSocialService {

    private final UserRepository userRepository;
    private final UserFollowRepository userFollowRepository;
    private final RefreshTokenRedisRepository tokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public MyProfileResponse getMyProfile(Long userId) {
        return MyProfileResponse.from(findActiveUser(userId));
    }

    @Transactional
    public MyProfileResponse updateMyProfile(Long userId, ProfileUpdateRequest request) {
        User user = findActiveUser(userId);
        if (request.nickname() != null && !request.nickname().isBlank()
                && !request.nickname().equals(user.getNickname())
                && userRepository.existsByNicknameAndDeletedAtIsNull(request.nickname())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        user.updateProfile(request.nickname(), request.profileImageUrl(), request.bio());
        return MyProfileResponse.from(user);
    }

    @Transactional
    public void withdraw(Long userId, String accessToken) {
        User user = findActiveUser(userId);
        user.softDelete();
        tokenRepository.deleteRefreshToken(userId);
        if (accessToken != null && jwtTokenProvider.validateToken(accessToken)) {
            tokenRepository.blacklistAccessToken(accessToken, jwtTokenProvider.getRemainingExpiration(accessToken));
        }
    }

    public PublicUserProfileResponse getPublicProfile(Long targetUserId, Long viewerId) {
        User target = findActiveUser(targetUserId);
        boolean followed = viewerId != null
                && !viewerId.equals(targetUserId)
                && userFollowRepository.existsByFollowerIdAndFollowingId(viewerId, targetUserId);
        return PublicUserProfileResponse.from(target, followed);
    }

    @Transactional
    public FollowToggleResponse toggleFollow(Long followerId, Long targetUserId) {
        if (followerId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.SELF_FOLLOW_NOT_ALLOWED);
        }
        User follower = findActiveUser(followerId);
        User target = findActiveUser(targetUserId);

        var existing = userFollowRepository.findByFollowerIdAndFollowingId(followerId, targetUserId);
        boolean following;
        if (existing.isPresent()) {
            userFollowRepository.delete(existing.get());
            userRepository.decrementFollowerCount(targetUserId);
            userRepository.decrementFollowingCount(followerId);
            following = false;
        } else {
            try {
                userFollowRepository.saveAndFlush(new UserFollow(follower, target));
                userRepository.incrementFollowerCount(targetUserId);
                userRepository.incrementFollowingCount(followerId);
                following = true;
            } catch (DataIntegrityViolationException ex) {
                following = true;
            }
        }
        User refreshedTarget = findActiveUser(targetUserId);
        User refreshedFollower = findActiveUser(followerId);
        return new FollowToggleResponse(following, refreshedTarget.getFollowerCount(), refreshedFollower.getFollowingCount());
    }

    public PagedListResponse<FollowUserItem> getFollowers(Long targetUserId, int page, int size) {
        findActiveUser(targetUserId);
        Page<UserFollow> result = userFollowRepository.findFollowers(
                targetUserId, PageRequest.of(Math.max(page, 0), safeSize(size)));
        return new PagedListResponse<>(
                result.getContent().stream().map(f -> FollowUserItem.from(f.getFollower())).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.hasNext()
        );
    }

    public PagedListResponse<FollowUserItem> getFollowings(Long targetUserId, int page, int size) {
        findActiveUser(targetUserId);
        Page<UserFollow> result = userFollowRepository.findFollowings(
                targetUserId, PageRequest.of(Math.max(page, 0), safeSize(size)));
        return new PagedListResponse<>(
                result.getContent().stream().map(f -> FollowUserItem.from(f.getFollowing())).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.hasNext()
        );
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private int safeSize(int size) {
        return (size > 0 && size <= 50) ? size : 20;
    }
}
