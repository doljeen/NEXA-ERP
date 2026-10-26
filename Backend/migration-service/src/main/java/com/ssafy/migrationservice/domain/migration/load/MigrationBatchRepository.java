package com.ssafy.migrationservice.domain.migration.load;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MigrationBatchRepository extends JpaRepository<MigrationBatch, String> {

    List<MigrationBatch> findByMigrationIdOrderByStartedAtAsc(Long migrationId);

    Optional<MigrationBatch> findByBatchIdAndMigrationId(String batchId, Long migrationId);

    boolean existsByFileIdAndStatus(Long fileId, BatchStatus status);

    boolean existsByMigrationIdAndStatus(Long migrationId, BatchStatus status);
}
