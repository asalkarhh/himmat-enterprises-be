package com.asalkartechworks.himmat.identity.repository;

import com.asalkartechworks.himmat.identity.model.AppUser;
import com.asalkartechworks.himmat.identity.model.UserRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByRole(UserRole role);

    long countByRoleAndIsActiveTrue(UserRole role);

    @Query("SELECT u FROM AppUser u WHERE " +
           "(:role IS NULL OR u.role = :role) AND " +
           "(:isActive IS NULL OR u.isActive = :isActive)")
    Page<AppUser> searchUsers(@Param("role") UserRole role,
                              @Param("isActive") Boolean isActive,
                              Pageable pageable);

    /**
     * Acquires pessimistic write locks on all currently active users with the given role.
     * Essential for protecting the invariant that at least one active OWNER must exist,
     * even during concurrent update/deactivation attempts.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM AppUser u WHERE u.role = :role AND u.isActive = true")
    List<AppUser> findActiveUsersForUpdate(@Param("role") UserRole role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM AppUser u WHERE u.id = :id")
    Optional<AppUser> findByIdForUpdate(@Param("id") Long id);
}
