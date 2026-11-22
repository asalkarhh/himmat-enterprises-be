package com.asalkartechworks.himmat.shared.idempotency.repository;

import com.asalkartechworks.himmat.shared.idempotency.model.BusinessEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BusinessEventRepository extends JpaRepository<BusinessEvent, Long> {

    Optional<BusinessEvent> findByRequestKey(String requestKey);

    /**
     * Obtains a PostgreSQL transaction-scoped advisory lock using the 32-bit hash of the requestKey.
     * Automatically released when the surrounding transaction commits or rolls back.
     */
    @Query(value = "SELECT pg_advisory_xact_lock(hashtext(:requestKey))", nativeQuery = true)
    void acquireAdvisoryLock(@Param("requestKey") String requestKey);
}
