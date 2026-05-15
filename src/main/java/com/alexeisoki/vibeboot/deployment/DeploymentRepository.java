package com.alexeisoki.vibeboot.deployment;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentRepository extends JpaRepository<Deployment, UUID> {
}

