package com.asalkartechworks.himmat.settings.repository;

import com.asalkartechworks.himmat.settings.model.ShopSettings;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShopSettingsRepository extends JpaRepository<ShopSettings, Long> {

    default Optional<ShopSettings> findSingleton() {
        return findById(ShopSettings.SINGLETON_ID);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ShopSettings s WHERE s.id = :id")
    Optional<ShopSettings> findByIdForUpdate(@Param("id") Long id);
}
