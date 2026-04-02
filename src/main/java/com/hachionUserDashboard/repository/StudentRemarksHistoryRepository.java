package com.hachionUserDashboard.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hachionUserDashboard.entity.StudentRemarksHistory;

public interface StudentRemarksHistoryRepository extends JpaRepository<StudentRemarksHistory, Long> {

	@Query(value = "SELECT id, remark, call_made_on, final_remark, last_call_made_on, co_ordinator, created_at, call_status FROM student_remarks_history WHERE student_id = :studentId ORDER BY created_at DESC", nativeQuery = true)
	List<Object[]> findRemarksRaw(@Param("studentId") String studentId);
}
