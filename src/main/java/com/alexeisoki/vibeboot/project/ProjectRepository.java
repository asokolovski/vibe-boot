package com.alexeisoki.vibeboot.project;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

// JpaRepository gives us database methods for Project objects, including CRUD
// methods inherited from CrudRepository like save, findById, findAll, and deleteById.
public interface ProjectRepository extends JpaRepository<Project, UUID> {
}
