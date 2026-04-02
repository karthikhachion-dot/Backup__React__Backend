package com.hachionUserDashboard.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.hachionUserDashboard.entity.Payment;
import com.hachionUserDashboard.entity.PaymentInstallment;

import jakarta.transaction.Transactional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

	@Query(value = """
			SELECT pi.*
			FROM payment_installments pi
			JOIN payments p ON pi.payment_id = p.payment_id
			WHERE p.student_id = :studentId
			  AND p.course_name = :courseName
			  AND (pi.received_pay IS NULL OR pi.received_pay <= 0)
			ORDER BY pi.installment_id
			LIMIT 1
			""", nativeQuery = true)
	PaymentInstallment findNextUnpaidInstallmentNative(@Param("studentId") String studentId,
			@Param("courseName") String courseName);

	@Modifying
	@Transactional
	@Query(value = "UPDATE payments " + "SET stop_reminder = :stopReminder " + "WHERE course_name = :courseName "
			+ "AND student_id = :studentId " + "AND email = :email", nativeQuery = true)
	int updateStopReminderNative(@Param("stopReminder") String stopReminder, @Param("courseName") String courseName,
			@Param("studentId") String studentId, @Param("email") String email);

	@Query("SELECT p FROM Payment p WHERE p.courseName = :courseName AND p.studentId = :studentId AND p.email = :email")
	Optional<Payment> findByCourseNameAndStudentIdAndEmail(@Param("courseName") String courseName,
			@Param("studentId") String studentId, @Param("email") String email);
}
