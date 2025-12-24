package com.hachionUserDashboard.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hachionUserDashboard.entity.RegisterStudent;
import com.hachionUserDashboard.repository.RegisterStudentRepository;
import com.hachionUserDashboard.service.EmailService;
import com.hachionUserDashboard.service.WebhookSenderService;

import jakarta.mail.MessagingException;

@CrossOrigin
@RestController
public class RegisterStudentController {

	@Autowired
	private RegisterStudentRepository repo;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private EmailService emailService;

	@Autowired
	private WebhookSenderService webhookSenderService;

//	@GetMapping("/registerstudent/{id}")
//	public ResponseEntity<RegisterStudent> getRegisterStudent(@PathVariable Integer id) {
//		return repo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
//	}
	@GetMapping("/registerstudent/{id}")
	public ResponseEntity<RegisterStudent> getRegisterStudent(@PathVariable Long id) {
	    return repo.findById(id)
	            .map(ResponseEntity::ok)
	            .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
	}

	@GetMapping("/registerstudent")
	public List<RegisterStudent> getAllRegisterStudent() {
		return repo.findAllOrderByDateDescNative();
	}

	@PostMapping("registerstudent/add")
	public ResponseEntity<String> addStudent(@RequestBody RegisterStudent student) throws MessagingException {

		if (student.getEmail() == null || student.getEmail().isBlank()) {
			throw new IllegalArgumentException("Email is required");
		}
		if (student.getMobile() == null || student.getMobile().isBlank()) {
			throw new IllegalArgumentException("Mobile number is required");
		}

		if (repo.existsByEmail(student.getEmail())) {
			throw new RuntimeException("Email already exists in the system");
		}
		if (repo.existsByMobile(student.getMobile())) {
			throw new RuntimeException("Mobile number already exists in the system");
		}
		student.setAdditional_email(null);
		student.setAdditional_phone(0);
		student.setDate(LocalDate.now());

		String tempPassword = "Hach@123";
		String hashedPassword = passwordEncoder.encode(tempPassword);
		student.setPassword(hashedPassword);

		String fullName = student.getUserName();
		student.setStudentId(generateNextStudentId());

		emailService.sendEmailForRegisterOfflineStudent(student.getEmail(), tempPassword, fullName);

		RegisterStudent save = repo.save(student);
		webhookSenderService.sendRegistrationDetailsOffline(save);
		return ResponseEntity.ok("Student added successfully");
	}

	private String generateNextStudentId() {
		String prefix = "HACH";
		String lastStudentId = repo.findTopByOrderByStudentIdDesc();

		int nextNumber = 1;

		if (lastStudentId != null && lastStudentId.startsWith(prefix)) {
			String numberPart = lastStudentId.substring(prefix.length());
			try {
				nextNumber = Integer.parseInt(numberPart) + 1;
			} catch (NumberFormatException e) {
				nextNumber = 1;
			}
		}

		return prefix + String.format("%03d", nextNumber);
	}

//	@PutMapping("/registerstudent/update/{id}")
//	public ResponseEntity<RegisterStudent> updateRegisterStudent(@PathVariable int id,
//			@RequestBody RegisterStudent updatedRegisterStudent) {
//		return repo.findById(id).map(registerstudent -> {
//			registerstudent.setUserName(updatedRegisterStudent.getUserName());
//			registerstudent.setMobile(updatedRegisterStudent.getMobile());
//			registerstudent.setEmail(updatedRegisterStudent.getEmail());
//			registerstudent.setLocation(updatedRegisterStudent.getLocation());
//			registerstudent.setCountry(updatedRegisterStudent.getCountry());
//			registerstudent.setTime_zone(updatedRegisterStudent.getTime_zone());
//			registerstudent.setCourse_name(updatedRegisterStudent.getCourse_name());
//			registerstudent.setAdditional_email(updatedRegisterStudent.getAdditional_email());
//			registerstudent.setAdditional_phone(updatedRegisterStudent.getAdditional_phone());
//			registerstudent.setPassword(updatedRegisterStudent.getPassword());
//			repo.save(registerstudent);
//			return ResponseEntity.ok(registerstudent);
//		}).orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
//	}
	
	@PutMapping("/registerstudent/update/{id}")
	public ResponseEntity<RegisterStudent> updateRegisterStudent(
	        @PathVariable Long id,
	        @RequestBody RegisterStudent req) {

	    return repo.findById(id).map(existing -> {

	        // ===== BASIC INFO =====
	        if (req.getUserName() != null)
	            existing.setUserName(req.getUserName());

	        if (req.getEmail() != null)
	            existing.setEmail(req.getEmail());

	        if (req.getMobile() != null)
	            existing.setMobile(req.getMobile());

	        if (req.getWhatsapp() != null)
	            existing.setWhatsapp(req.getWhatsapp());
	        
	        if (req.getCountry() != null)
	            existing.setCountry(req.getCountry());

	        if (req.getLocation() != null)
	            existing.setLocation(req.getLocation());

	        if (req.getVisa_status() != null)
	            existing.setVisa_status(req.getVisa_status());

	        if (req.getTime_zone() != null)
	            existing.setTime_zone(req.getTime_zone());

	        if (req.getAnalyst_name() != null)
	            existing.setAnalyst_name(req.getAnalyst_name());

	        // ===== BUSINESS FIELDS (YOUR BUG WAS HERE) =====
	        if (req.getSource() != null)
	            existing.setSource(req.getSource());

	        if (req.getRemarks() != null)
	            existing.setRemarks(req.getRemarks());

	        if (req.getComments() != null)
	            existing.setComments(req.getComments());

	        if (req.getSend_details() != null)
	            existing.setSend_details(req.getSend_details());

	        if (req.getCourse_name() != null)
	            existing.setCourse_name(req.getCourse_name());

	        if (req.getMode() != null)
	            existing.setMode(req.getMode());

	        // ===== OPTIONAL CONTACT FIELDS =====
	        if (req.getAdditional_email() != null)
	            existing.setAdditional_email(req.getAdditional_email());

	        if (req.getAdditional_phone() != null)
	            existing.setAdditional_phone(req.getAdditional_phone());

	        // ===== PROFILE FIELDS =====
	        if (req.getFirstName() != null)
	            existing.setFirstName(req.getFirstName());

	        if (req.getLastName() != null)
	            existing.setLastName(req.getLastName());

	        if (req.getDob() != null)
	            existing.setDob(req.getDob());

	        if (req.getGender() != null)
	            existing.setGender(req.getGender());

	        if (req.getAddress() != null)
	            existing.setAddress(req.getAddress());

	        if (req.getBio() != null)
	            existing.setBio(req.getBio());

	        if (req.getProfileImage() != null)
	            existing.setProfileImage(req.getProfileImage());

	        // ===== SOCIAL LINKS =====
	        if (req.getFacebook() != null)
	            existing.setFacebook(req.getFacebook());

	        if (req.getTwitter() != null)
	            existing.setTwitter(req.getTwitter());

	        if (req.getLinkedin() != null)
	            existing.setLinkedin(req.getLinkedin());

	        if (req.getWebsite() != null)
	            existing.setWebsite(req.getWebsite());

	        if (req.getGithub() != null)
	            existing.setGithub(req.getGithub());

	        // ===== SECURITY / OTP (DO NOT AUTO-RESET) =====
	        if (req.getOTP() != null)
	            existing.setOTP(req.getOTP());

	        if (req.getOTPStatus() != null)
	            existing.setOTPStatus(req.getOTPStatus());

	        if (req.getOtpGeneratedTime() != null)
	            existing.setOtpGeneratedTime(req.getOtpGeneratedTime());

	        // ===== PASSWORD (ONLY IF SENT) =====
	        if (req.getPassword() != null && !req.getPassword().isBlank())
	            existing.setPassword(req.getPassword());

	        // ===== STATUS =====
	        if (req.getStatus() != null)
	            existing.setStatus(req.getStatus());

	        // ===== AUDIT =====
	        existing.setDate(LocalDate.now()); // update date on edit

	        repo.save(existing);
	        return ResponseEntity.ok(existing);

	    }).orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
	}


//	@DeleteMapping("registerstudent/delete/{id}")
//	public ResponseEntity<?> deleteRegisterStudent(@PathVariable int id) {
//		repo.deleteById(id);
//		return ResponseEntity.ok("Student deleted successfully.");
//	}

	@DeleteMapping("registerstudent/delete/{id}")
	public ResponseEntity<?> deleteRegisterStudent(@PathVariable Long id) {
	    repo.deleteById(id);
	    return ResponseEntity.ok("Student deleted successfully.");
	}

	@GetMapping("/check-mobile")
	public ResponseEntity<?> checkMobileExists(@RequestParam String mobile) {
		int count = repo.countByMobile(mobile);
		if (count > 0) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body("Mobile number already exists");
		} else {
			return ResponseEntity.ok("Mobile number is available");
		}
	}
}