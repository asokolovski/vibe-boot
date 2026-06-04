package com.alexeisoki.vibeboot.project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectEnvironmentVariableRepository extends JpaRepository<ProjectEnvironmentVariable, UUID> {

    boolean existsByProjectIdAndKey(UUID projectId, String key);

    List<ProjectEnvironmentVariable> findByProjectIdOrderByCreatedAtAsc(UUID projectId);

    Optional<ProjectEnvironmentVariable> findByIdAndProjectId(UUID id, UUID projectId);
}
