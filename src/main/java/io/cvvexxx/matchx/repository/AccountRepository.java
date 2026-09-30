package io.cvvexxx.matchx.repository;

import io.cvvexxx.matchx.entity.account.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.user.id = :userId AND a.asset = :asset")
    Optional<Account> findByUserIdAndAssetForUpdate(UUID userId, String asset);

    Optional<List<Account>> findAllByUserId(UUID userId);

}
