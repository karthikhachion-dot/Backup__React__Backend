package com.hachionUserDashboard.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hachionUserDashboard.entity.ToolsEntity;

public interface ToolsRepository extends JpaRepository<ToolsEntity, Long> {

	Optional<ToolsEntity> findByCategoryNameAndCourseName(String categoryName, String courseName);

	void deleteByCategoryNameAndCourseName(String categoryName, String courseName);

	List<ToolsEntity> findByCourseName(String courseName);

}
