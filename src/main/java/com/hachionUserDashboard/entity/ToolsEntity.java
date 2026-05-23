package com.hachionUserDashboard.entity;

import java.time.LocalDate;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "course_tools")
public class ToolsEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "curr_id")
	private Long currId;

	@Column(name = "category_name",nullable = false)
	private String categoryName;

	@Column(name = "course_name",nullable = false)
	private String courseName;

	@Column(name = "created_date", nullable = false)
	private LocalDate createdDate;

	@OneToMany(mappedBy = "tools", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ToolsItemEntity> items;

	// getters & setters

	public Long getCurrId() {
		return currId;
	}

	public void setCurrId(Long currId) {
		this.currId = currId;
	}

	public String getCategoryName() {
		return categoryName;
	}

	public void setCategoryName(String categoryName) {
		this.categoryName = categoryName;
	}

	public String getCourseName() {
		return courseName;
	}

	public void setCourseName(String courseName) {
		this.courseName = courseName;
	}

	public LocalDate getCreatedDate() {
		return createdDate;
	}

	public void setCreatedDate(LocalDate createdDate) {
		this.createdDate = createdDate;
	}

	public List<ToolsItemEntity> getItems() {
		return items;
	}

	public void setItems(List<ToolsItemEntity> items) {
		this.items = items;
	}
}
