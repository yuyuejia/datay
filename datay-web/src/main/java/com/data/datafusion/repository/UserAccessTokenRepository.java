package com.data.datafusion.repository;

import com.data.datafusion.domain.UserAccessToken;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link UserAccessToken} entity.
 */
@Repository
public interface UserAccessTokenRepository extends JpaRepository<UserAccessToken, String> {
    List<UserAccessToken> findByUserIdOrderByCreateTimeDesc(Long userId);

    Optional<UserAccessToken> findByLookupHashAndStatus(String lookupHash, String status);

    Optional<UserAccessToken> findByIdAndUserId(String id, Long userId);

    long countByUserId(Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(Long userId, String name, String id);

    boolean existsByLookupHash(String lookupHash);

    @Modifying
    @Query("update UserAccessToken token set token.lastUsedTime = :lastUsedTime where token.id = :id")
    int updateLastUsedTime(@Param("id") String id, @Param("lastUsedTime") ZonedDateTime lastUsedTime);
}
