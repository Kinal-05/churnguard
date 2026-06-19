package com.churnguard.repository;

import com.churnguard.entity.ModelVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ModelVersionRepository extends JpaRepository<ModelVersion, UUID> {
    Optional<ModelVersion> findByVersion(String version);
    Optional<ModelVersion> findByIsActiveTrue();
}