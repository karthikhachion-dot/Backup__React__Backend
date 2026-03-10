package com.hachionUserDashboard.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.hachionUserDashboard.dto.CertificateDTO;
import com.hachionUserDashboard.dto.CertificateRequest;
import com.hachionUserDashboard.dto.CertificatesResponse;
import com.hachionUserDashboard.entity.CertificateEntity;
import com.hachionUserDashboard.repository.CertificateDetailsRepository;
import com.hachionUserDashboard.repository.CourseRepository;
import com.hachionUserDashboard.repository.EnrollRepository;
import com.hachionUserDashboard.repository.ToolsRepository;

import Service.CertificateService;
import jakarta.mail.MessagingException;
import jakarta.transaction.Transactional;

@Service
public class Certificateimp implements CertificateService {

	@Autowired
	private CertificateDetailsRepository certificateRepository;

	@Autowired
	private EmailService emailService;

	@Value("${certificate.base-path}")
	private String certificateBasePath;

	@Autowired
	private CourseRepository courseRepository;
	
	@Autowired
	private EnrollRepository enrollRepository;
	
	@Autowired
	private ToolsRepository toolsRepository;

	
	private String sanitizeText(String text) {
	    if (text == null) return "";

	    return text
	            .replace("\u202F", " ")   
	            .replace("\u00A0", " ")   
	            .replace("\u2011", "-")   
	            .replace("\u2013", "-")   
	            .replace("\u2014", "-")   
	            .replaceAll("[^\\x00-\\x7F]", ""); 
	}
	
	@Override
	public CertificateEntity generateCertificate(CertificateRequest request) {

		Optional<CertificateEntity> existingEntity = certificateRepository
				.findByStudentIdAndCourseName(request.getStudentId(), request.getCourseName());

		if (existingEntity.isPresent()) {

			return existingEntity.get();
		}

		String path = generateCertificatePdf(request.getStudentName(), request.getStudentId(), request.getCourseName(),
				request.getCompletionDate());

		if (path == null) {
			throw new RuntimeException("PDF generation failed. Certificate will not be saved.");
		}

		CertificateEntity entity = new CertificateEntity();
		entity.setStudentId(request.getStudentId());
		entity.setStudentName(request.getStudentName());
		entity.setStudentEmail(request.getStudentEmail());
		entity.setCourseName(request.getCourseName());

		LocalDate parsedDate = LocalDate.parse(request.getCompletionDate().trim());
		entity.setCompletionDate(parsedDate.toString());
		entity.setStatus(request.getStatus());
		entity.setGrade(request.getGrade());

		entity = certificateRepository.save(entity);

		entity.setCertificatePath(path);
		certificateRepository.save(entity);

		return entity;
	}
	public String generateCertificatePdf(String studentName, String studentId, String courseName,
	        String completionDate) {

		studentName = sanitizeText(studentName);
		courseName = sanitizeText(courseName);
		studentId = sanitizeText(studentId);
		completionDate = sanitizeText(completionDate);
		
		
	    String folderPath = certificateBasePath;
	    String outputPdfPath = folderPath + studentId + "_" + courseName.replaceAll("\\s+", "_") + "_Certificate.pdf";
	    String inputPdfPath = folderPath + "Hachion's CertificateFinal.pdf";

	    try {
	        File folder = new File(folderPath);
	        if (!folder.exists()) {
	            folder.mkdirs();
	        }

	        File outputFile = new File(outputPdfPath);
	        if (outputFile.exists()) {
	            return outputPdfPath;
	        }

	        PDDocument document = PDDocument.load(new File(inputPdfPath));
	        document.setAllSecurityToBeRemoved(true);

	        PDPage page = document.getPage(0);
	        PDRectangle box = page.getCropBox();
	        float pageWidth = box.getWidth();
	        float pageHeight = box.getHeight();
	        int rotation = page.getRotation();

	        PDPageContentStream contentStream = new PDPageContentStream(document, page,
	                PDPageContentStream.AppendMode.APPEND, true, true);

	        if (rotation == 90) {
	            contentStream.transform(new org.apache.pdfbox.util.Matrix(0, 1, -1, 0, pageHeight, 0));
	        } else if (rotation == 180) {
	            contentStream.transform(new org.apache.pdfbox.util.Matrix(-1, 0, 0, -1, pageWidth, pageHeight));
	        } else if (rotation == 270) {
	            contentStream.transform(new org.apache.pdfbox.util.Matrix(0, -1, 1, 0, 0, pageWidth));
	        }


	        
	        PDFont fontBold = PDType1Font.HELVETICA_BOLD;
	        PDFont fontItalic = PDType1Font.HELVETICA;
	        PDFont fontBoldForCourseName = PDType1Font.HELVETICA_BOLD;

	        int nameFontSize = 24;
	        int courseFontSize = 24;
	        int dateFontSize = 14;
	        int aboutCourseFontSize = 12;

	        float nameWidth = fontBold.getStringWidth(studentName) / 1000 * nameFontSize;
	        float courseWidth = fontBoldForCourseName.getStringWidth(courseName) / 1000 * courseFontSize;

	        // --- Hours from course table ---
	        String aboutCourse = sanitizeText(courseRepository.findAboutCourseByCourseName(courseName));
	        String classesStr = aboutCourse != null ? aboutCourse : "0";
	        int totalHours = 0;
	        try {
	            totalHours = Integer.parseInt(classesStr);
	        } catch (NumberFormatException e) {
	            totalHours = 0;
	        }

	        String mode = sanitizeText(enrollRepository.findModeByStudentAndCourse(studentId, courseName));

	        if (mode == null || mode.trim().isEmpty()) {
	            mode = "Instructor-Led Training";
	        }

	        String safeMode = sanitizeText(mode.trim());
	        
	        String aboutText =
	            "This is to certify that the above-named candidate has successfully completed the " +
	            courseName +
	            " Certification Program conducted by Hachion, comprising " +
	            totalHours +
	            " hours of " + safeMode + ", and has demonstrated proficiency in core concepts " +
	            "and practical applications aligned with industry standards.";
	        

	        // =======================
	        // 1) Student Name
	        // =======================
	        contentStream.beginText();
	        contentStream.setFont(fontBold, nameFontSize);
	        contentStream.setNonStrokingColor(0.055f, 0.286f, 0.659f);

	        float whiteAreaStartXForName = pageWidth * 0.28f;
	        float whiteAreaWidthForName = pageWidth * 0.70f;
	        float nameX = whiteAreaStartXForName + (whiteAreaWidthForName - nameWidth) / 2;
	        contentStream.newLineAtOffset(nameX, pageHeight * 0.57f);

	        contentStream.showText(studentName);
	        contentStream.endText();

	        // =======================
	        // 3) About text (wrapped, bold parts)
	        // =======================
	        float safeWhiteStartX = pageWidth * 0.35f;
	        float safeWhiteWidth = pageWidth * 0.55f;

	        String[] words = aboutText.split(" ");
	        List<String> lines = new ArrayList<>();
	        StringBuilder currentLine = new StringBuilder();

	        for (String word : words) {
	            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
	            float testWidth = fontItalic.getStringWidth(testLine) / 1000 * aboutCourseFontSize;

	            if (testWidth > safeWhiteWidth) {
	                lines.add(currentLine.toString());
	                currentLine = new StringBuilder(word);
	            } else {
	                currentLine = new StringBuilder(testLine);
	            }
	        }

	        if (currentLine.length() > 0) {
	            lines.add(currentLine.toString());
	        }

	        float startY = pageHeight * 0.48f;
	        float lineGap = aboutCourseFontSize + 4;

	        String hoursPhrase = totalHours + " hours";


	        String modePhrase = mode.trim();
	        
	        for (int i = 0; i < lines.size(); i++) {
	            String line = lines.get(i);

	            float lineWidth = fontItalic.getStringWidth(line) / 1000 * aboutCourseFontSize;
	            float lineX = safeWhiteStartX + (safeWhiteWidth - lineWidth) / 2;
	            float lineY = startY - (i * lineGap);

	            contentStream.beginText();
	            contentStream.setNonStrokingColor(0f, 0f, 0f);
	            contentStream.newLineAtOffset(lineX, lineY);

	            String text = line;

	            
	            String remaining = text;

	            
	         // 1) Course name (Force Bold Properly)
	            if (remaining.contains(courseName)) {

	                int index = remaining.indexOf(courseName);

	                String before = remaining.substring(0, index);
	                String after = remaining.substring(index + courseName.length());

	                // Print text before course name
	                contentStream.setFont(fontItalic, aboutCourseFontSize);
	                contentStream.showText(before);

	                // Print course name in bold
	                contentStream.setFont(PDType1Font.HELVETICA_BOLD, aboutCourseFontSize);
	                contentStream.showText(courseName);

	                remaining = after;
	            }
	            // 2) Hours
	            if (remaining.contains(hoursPhrase)) {
	                String[] p = remaining.split(java.util.regex.Pattern.quote(hoursPhrase), 2);

	                contentStream.setFont(fontItalic, aboutCourseFontSize);
	                contentStream.showText(p[0]);

	                contentStream.setFont(fontBold, aboutCourseFontSize);
	                contentStream.showText(hoursPhrase);

	                remaining = p.length > 1 ? p[1] : "";
	            }

	           
	            if (remaining.contains(modePhrase)) {
	                String[] p = remaining.split(java.util.regex.Pattern.quote(modePhrase), 2);

	                contentStream.setFont(fontBold, aboutCourseFontSize);
	                contentStream.showText(p[0]);

	                
	                contentStream.setFont(fontBold, aboutCourseFontSize);
	                contentStream.showText(modePhrase);

	                remaining = p.length > 1 ? p[1] : "";
	            }

	            // 4) Whatever is left
	            contentStream.setFont(fontItalic, aboutCourseFontSize);
	            contentStream.showText(remaining);

	            contentStream.endText();
	        }


	        // =======================
	        // 5) Student ID
	        // =======================
	        contentStream.beginText();
	        contentStream.setFont(fontBold, 12);
	        contentStream.setNonStrokingColor(0f, 0f, 0f);
	        contentStream.newLineAtOffset(pageWidth * 0.39f, pageHeight * 0.10f);
	        contentStream.showText(studentId);
	        contentStream.endText();
	        
	     // =======================
	     // 6) Left Side: Key Skills (Static)
	     // =======================
	        PDFont skillsFont = PDType1Font.HELVETICA;
	     int skillsFontSize = 14;

	     
	     contentStream.setNonStrokingColor(1f, 1f, 1f);

	     
	     float skillsStartX = pageWidth * 0.05f;   
	     float skillsStartY = pageHeight * 0.41f;  
	     float lineGapSkills = 20;

	     List<String> skillsList = toolsRepository.findToolNamesByCourseName(courseName);

	     if (skillsList != null) {
	         skillsList = skillsList.stream()
	                 .filter(skill -> skill != null && !skill.trim().equalsIgnoreCase("N/A"))
	                 .map(this::sanitizeText)
	                 .filter(skill -> !skill.trim().isEmpty())
	                 .toList();
	     }

	     if (skillsList != null && !skillsList.isEmpty()) {
	         for (int i = 0; i < skillsList.size(); i++) {
	             contentStream.beginText();
	             contentStream.setFont(skillsFont, skillsFontSize);
	             contentStream.newLineAtOffset(skillsStartX, skillsStartY - (i * lineGapSkills));
	             contentStream.showText(skillsList.get(i));
	             contentStream.endText();
	         }
	     }

	     LocalDate date2 = LocalDate.parse(completionDate);

	    
	     String formattedDate2 = date2.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

	     String footerText = 
	             "    Completion Date: " + formattedDate2;

	     contentStream.beginText();
	     contentStream.setFont(fontBold, 11);
	     contentStream.setNonStrokingColor(0f, 0f, 0f);

	     
	     contentStream.newLineAtOffset(pageWidth * 0.72f, pageHeight * 0.10f);

	     contentStream.showText(footerText);
	     contentStream.endText();

	        contentStream.close();
	        document.save(outputPdfPath);
	        document.close();

	        return outputPdfPath;

	    } catch (Exception e) {
	        e.printStackTrace();
	        throw new RuntimeException("Error while generating certificate PDF: " + e.getMessage(), e);
	    }
	}

	@Override
	public void sendCertificateByEmail(Long certificateId) throws IOException, MessagingException {
		CertificateEntity certificate = certificateRepository.findById(certificateId)
				.orElseThrow(() -> new RuntimeException("Certificate not found"));

		String email = certificate.getStudentEmail();
		String filePath = certificate.getCertificatePath();

		byte[] pdfBytes = Files.readAllBytes(Paths.get(filePath));

		emailService.sendEmailWithAttachment(email, pdfBytes, "Your Course Certificate",
				"Please find attached your certificate.");
	}

	@Override
	public List<CertificateEntity> getAllCertificates() {
		return certificateRepository.findAll();
	}

	@Override
	public String getUserById(Long certificateId) {

		return null;
	}

	public List<CertificateEntity> getCertificatesByStudentName(String studentName) {
		List<CertificateEntity> list = certificateRepository.findByStudentNameNative(studentName);
		return list != null ? list : new ArrayList<>();
	}

	@Transactional
	public CertificatesResponse getByEmail(String email) {
		var rows = certificateRepository.findAllByStudentEmail(email);
		List<CertificateDTO> items = rows.stream()
				.map(r -> new CertificateDTO(r.getId(), r.getCourseName(), r.getGrade(), r.getIssueDate(),
						
						r.getId() != null ? "CERT-" + r.getId() : null, r.getCertificatePath()))
				.collect(Collectors.toList());

		long total = certificateRepository.countByStudentEmail(email); 
		return new CertificatesResponse(total, items);
	}

	@Transactional
	public long countByEmail(String email) {
		return certificateRepository.countByStudentEmail(email);
	}

	public List<CertificateEntity> findByStudentNameIgnoreCase(String studentName) {
		
		return null;
	}

}
