package com.hachionUserDashboard.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.hachionUserDashboard.dto.ImportResponse;
import com.hachionUserDashboard.dto.StudentRemarkRequest;
import com.hachionUserDashboard.dto.StudentRemarkResponse;
import com.hachionUserDashboard.entity.RegisterStudent;
import com.hachionUserDashboard.entity.StudentRemarksHistory;
import com.hachionUserDashboard.repository.RegisterStudentRepository;
import com.hachionUserDashboard.repository.StudentRemarksHistoryRepository;

@Service
public class RegisterStudentService {

	@Autowired
	private RegisterStudentRepository repository;

	@Autowired
	private StudentRemarksHistoryRepository studentRemarksHistoryRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private EmailService emailService;

	public ImportResponse importExcel(MultipartFile file) throws Exception {

		List<RegisterStudent> validList = new ArrayList<>();
		Set<String> dbDuplicateEmails = new HashSet<>();
		Set<String> excelDuplicateEmails = new HashSet<>();
		Set<String> excelEmails = new HashSet<>();
		List<String> invalidNumbers = new ArrayList<>();

		String fileName = file.getOriginalFilename();

		if (fileName == null || (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls"))) {
			throw new RuntimeException("Only Excel files (.xlsx, .xls) are allowed");
		}
		String contentType = file.getContentType();

		if (contentType == null
				|| (!contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
						&& !contentType.equals("application/vnd.ms-excel"))) {
			throw new RuntimeException("Invalid file type. Please upload Excel file only");
		}
//		Workbook workbook = new XSSFWorkbook(file.getInputStream());

		Workbook workbook = WorkbookFactory.create(file.getInputStream());
		Sheet sheet = workbook.getSheetAt(0);

		int totalRows = sheet.getPhysicalNumberOfRows();

		boolean useDBCheck = (totalRows - 1) <= 50;

		Set<String> existingEmails = new HashSet<>();
		int studentIdCounter = 0;

		String prefix = "HACH";
		String lastStudentId = repository.findTopByOrderByStudentIdDesc();

		int baseNumber = 0;

		if (lastStudentId != null && lastStudentId.startsWith(prefix)) {
			String numberPart = lastStudentId.substring(prefix.length());
			try {
				baseNumber = Integer.parseInt(numberPart);
			} catch (NumberFormatException e) {
				baseNumber = 0;
			}
		}

		if (!useDBCheck) {

			List<String> emailsFromDB = repository.findAllEmails();
			existingEmails = emailsFromDB.stream().filter(Objects::nonNull).map(String::toLowerCase)
					.collect(Collectors.toSet());
		}
		String tempPassword = "Hach@123";
		String hashedPassword = passwordEncoder.encode(tempPassword);
		for (int i = 1; i < totalRows; i++) {

			Row row = sheet.getRow(i);
			if (row == null)
				continue;

			RegisterStudent student = new RegisterStudent();

			student.setDate(getLocalDate(row.getCell(0)));
			student.setAnalyst_name(getString(row.getCell(1)));
			student.setSeoTeam(getString(row.getCell(2)));
			student.setSource(getString(row.getCell(3)));
			student.setUserName(getString(row.getCell(4)));
			String email = getString(row.getCell(5));
			if (email != null)
				email = email.toLowerCase();
			student.setEmail(email);
			student.setCourse_name(getString(row.getCell(6)));

			String rawMobile = getString(row.getCell(7));
			String rawWhatsapp = getString(row.getCell(8));

			String mobile = formatPhoneNumber(rawMobile);
			String whatsapp = formatPhoneNumber(rawWhatsapp);

			// 👉 If invalid → keep original value
			if (mobile == null && rawMobile != null) {
			    mobile = rawMobile;
			    invalidNumbers.add("Mobile: " + rawMobile);
			}

			if (whatsapp == null && rawWhatsapp != null) {
			    whatsapp = rawWhatsapp;
			    invalidNumbers.add("Whatsapp: " + rawWhatsapp);
			}

			student.setMobile(mobile);
			student.setWhatsapp(whatsapp);
			
			student.setCountry(getString(row.getCell(9)));
			student.setStateCity(getString(row.getCell(10)));
			student.setTime_zone(getString(row.getCell(11)));
			student.setCoordinator(getString(row.getCell(12)));
			String remarks = getString(row.getCell(13));
			String callMadeOn = getString(row.getCell(14));
			String finalRemark = getString(row.getCell(15));
			String lastCallMadeOn = getString(row.getCell(16));
			student.setLeadStatus(getString(row.getCell(17)));

			// CREATE HISTORY OBJECT (only if any value exists)
			StudentRemarksHistory history = null;

			if ((remarks != null && !remarks.isEmpty()) || (callMadeOn != null && !callMadeOn.isEmpty())
					|| (finalRemark != null && !finalRemark.isEmpty())
					|| (lastCallMadeOn != null && !lastCallMadeOn.isEmpty())) {

				history = new StudentRemarksHistory();

				history.setRemark(remarks);
				history.setCallMadeOn(callMadeOn);
				history.setFinalRemark(finalRemark);
				history.setLastCallMadeOn(lastCallMadeOn);
				history.setCoordinator(student.getCoordinator());
				history.setCreatedAt(LocalDate.now());
			}
			student.setMode("Offline");

			student.setPassword(hashedPassword);

			if (email == null || email.isEmpty())
				continue;

			boolean isDbDuplicate;
			boolean isExcelDuplicate = excelEmails.contains(email);

			if (useDBCheck) {
				isDbDuplicate = repository.existsByEmail(email);
			} else {
				isDbDuplicate = existingEmails.contains(email);
			}

			if (isExcelDuplicate) {
				excelDuplicateEmails.add(email);
			}

			if (isDbDuplicate) {
				dbDuplicateEmails.add(email);
			}

			boolean isDuplicate = isDbDuplicate || isExcelDuplicate;

			if (isDuplicate) {

			} else {

				int nextNumber = baseNumber + 1 + studentIdCounter++;
				student.setStudentId(prefix + String.format("%03d", nextNumber));

				validList.add(student);
				if (history != null) {
					history.setStudent(student);
					student.setRemarksHistory(List.of(history));
				}
				excelEmails.add(email);

				existingEmails.add(email);
			}
		}

		
		repository.saveAll(validList);

		for (RegisterStudent student : validList) {
			emailService.sendEmailForRegisterOfflineStudent(student.getEmail(), "Hach@123", student.getUserName());
		}
		workbook.close();

		ImportResponse response = new ImportResponse();
		response.setTotalRecords(totalRows - 1);
		response.setSavedRecords(validList.size());
		response.setDuplicateRecords(dbDuplicateEmails.size() + excelDuplicateEmails.size());
		response.setDbDuplicateEmails(new ArrayList<>(dbDuplicateEmails));
		response.setExcelDuplicateEmails(new ArrayList<>(excelDuplicateEmails));
		response.setInvalidNumbers(invalidNumbers);
		
		return response;
	}

	private String getString(Cell cell) {
		if (cell == null)
			return null;

		DataFormatter formatter = new DataFormatter();
		return formatter.formatCellValue(cell).trim();
	}

	private LocalDate getLocalDate(Cell cell) {
		if (cell == null)
			return LocalDate.now();

		if (cell.getCellType() == CellType.NUMERIC) {
			return cell.getLocalDateTimeCellValue().toLocalDate();
		} else {
			return LocalDate.parse(cell.toString());
		}
	}

	public StudentRemarkResponse addStudentRemark(StudentRemarkRequest request) {

		// ✅ Step 1: Validate studentId exists (NO full fetch)
		if (!repository.existsByStudentId(request.getStudentId())) {
			throw new RuntimeException("Student not found");
		}

		// ✅ Step 2: Create lightweight parent reference (NO DB hit)
		RegisterStudent student = repository.findByStudentId(request.getStudentId())
		        .orElseThrow(() -> new RuntimeException("Student not found"));

		// ✅ Step 3: Create child
		StudentRemarksHistory history = new StudentRemarksHistory();
		history.setRemark(request.getRemark());
		history.setCallMadeOn(request.getCallMadeOn());
		history.setFinalRemark(request.getFinalRemark());
		history.setLastCallMadeOn(request.getLastCallMadeOn());
		history.setCoordinator(request.getCoordinator());
		history.setCreatedAt(java.time.LocalDate.now());
		history.setCallStatus(request.getCallStatus());

		// ✅ Step 4: Set relationship
		history.setStudent(student);

		// ✅ Step 5: Save ONLY child
		StudentRemarksHistory savedHistory = studentRemarksHistoryRepository.save(history);

		// Convert to response DTO
		StudentRemarkResponse response = new StudentRemarkResponse();
		response.setId(savedHistory.getId());
		response.setStudentId(savedHistory.getStudent().getStudentId());
		response.setRemark(savedHistory.getRemark());
		response.setCallMadeOn(savedHistory.getCallMadeOn());
		response.setFinalRemark(savedHistory.getFinalRemark());
		response.setLastCallMadeOn(savedHistory.getLastCallMadeOn());
		response.setCoordinator(savedHistory.getCoordinator());
		response.setCreatedAt(savedHistory.getCreatedAt());
		response.setCallStatus(savedHistory.getCallStatus());

		return response;
	}
	public List<StudentRemarksHistory> getRemarksByStudentId(String studentId) {

	    List<Object[]> rows = studentRemarksHistoryRepository.findRemarksRaw(studentId);

	    List<StudentRemarksHistory> list = new ArrayList<>();

	    for (Object[] row : rows) {
	        StudentRemarksHistory r = new StudentRemarksHistory();

	        r.setId(((Number) row[0]).longValue());
	        r.setRemark((String) row[1]);
	        r.setCallMadeOn((String) row[2]);
	        r.setFinalRemark((String) row[3]);
	        r.setLastCallMadeOn((String) row[4]);
	        r.setCoordinator((String) row[5]);
	        r.setCreatedAt(row[6] != null ? ((java.sql.Date) row[6]).toLocalDate() : null);
	        r.setCallStatus((String) row[7]);

	        list.add(r);
	    }

	    return list;
	}
	private String formatPhoneNumber(String raw) {

	    if (raw == null || raw.trim().isEmpty()) return null;

	    // 1. Handle multiple numbers (take first)
	    if (raw.contains("/")) {
	        raw = raw.split("/")[0];
	    }

	    // 2. Remove all non-digits
	    String cleaned = raw.replaceAll("[^\\d]", "");

	    if (cleaned.isEmpty()) return null;

	    // ===============================
	    // 🔥 AUTO DETECTION LOGIC
	    // ===============================

	    // 🇮🇳 INDIA
	    if (cleaned.startsWith("91") && cleaned.length() == 12) {
	        return "+91 " + cleaned.substring(2);
	    }

	    if (cleaned.length() == 10 && cleaned.matches("[6-9]\\d{9}")) {
	        return "+91 " + cleaned;
	    }

	    // 🇺🇸 USA
	    if (cleaned.startsWith("1") && cleaned.length() == 11) {
	        return "+1 " + cleaned.substring(1);
	    }

	    if (cleaned.length() == 10) {
	        return "+1 " + cleaned;
	    }

	    // 🇬🇧 UK
	    if (cleaned.startsWith("44") && cleaned.length() >= 12) {
	        return "+44 " + cleaned.substring(2);
	    }

	    // 🌍 Generic fallback (if starts with country code)
	    if (cleaned.length() > 11 && cleaned.length() <= 13) {

	        // Try extracting first 1–3 digits as country code
	        if (cleaned.startsWith("91")) {
	            return "+91 " + cleaned.substring(2);
	        }
	        if (cleaned.startsWith("1")) {
	            return "+1 " + cleaned.substring(1);
	        }
	        if (cleaned.startsWith("44")) {
	            return "+44 " + cleaned.substring(2);
	        }
	    }

	    return null; // invalid
	}
}