package com.cosmetic.ecommerce.repository;

import com.cosmetic.ecommerce.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {
    // Projection avoids caching a stale token before the account row lock is acquired.
    @Query("select t.user.id from PasswordResetToken t where t.tokenHash = :hash")
    Long findOwnerId(@Param("hash") String hash);

    @Modifying(flushAutomatically = true)
    @Query("update PasswordResetToken t set t.usedAt = :now where t.user.id = :userId and t.usedAt is null")
    int consumeAll(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from PasswordResetToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
