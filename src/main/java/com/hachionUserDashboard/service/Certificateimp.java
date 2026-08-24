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
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.hachionUserDashboard.dto.CertificateDTO;
import com.hachionUserDashboard.dto.CertificateRequest;
import com.hachionUserDashboard.dto.CertificatesResponse;
import com.hachionUserDashboard.entity.CertificateEntity;
import com.hachionUserDashboard.entity.ToolsEntity;
import com.hachionUserDashboard.entity.ToolsItemEntity;
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

		CertificateEntity entity = new CertificateEntity();
		entity.setStudentId(request.getStudentId());
		entity.setStudentName(request.getStudentName());
		entity.setStudentEmail(request.getStudentEmail());
		entity.setCourseName(request.getCourseName());

		LocalDate parsedDate = LocalDate.parse(request.getCompletionDate().trim());
		entity.setCompletionDate(parsedDate.toString());
		entity.setStatus(request.getStatus());
		entity.setGrade(request.getGrade());

		// Saved first (without a path yet) purely to obtain the real,
		// database-assigned certificateId - the new certificate background
		// (Certificate_Of_Hachion) has a "Certificate ID" footer slot that a
		// fabricated/placeholder number would make meaningless, and the ID
		// doesn't exist until JPA's IDENTITY strategy assigns it on insert.
		entity = certificateRepository.save(entity);

		String path = generateCertificatePdf(request.getStudentName(), request.getStudentId(), request.getCourseName(),
				request.getCompletionDate(), entity.getCertificateId());

		if (path == null) {
			throw new RuntimeException("PDF generation failed. Certificate will not be saved.");
		}

		entity.setCertificatePath(path);
		certificateRepository.save(entity);

		return entity;
	}
	public String generateCertificatePdf(String studentName, String studentId, String courseName,
	        String completionDate, Long certificateId) {

		studentName = sanitizeText(studentName);
		courseName = sanitizeText(courseName);
		studentId = sanitizeText(studentId);
		completionDate = sanitizeText(completionDate);


	    String folderPath = certificateBasePath;
	    String outputPdfPath = folderPath + studentId + "_" + courseName.replaceAll("\\s+", "_") + "_Certificate.pdf";

	    try {
	        File folder = new File(folderPath);
	        if (!folder.exists()) {
	            folder.mkdirs();
	        }

	        File outputFile = new File(outputPdfPath);
	        if (outputFile.exists()) {
	            return outputPdfPath;
	        }

	        // Certificate background is Certificate_Genarated.jpg - a
	        // "Key Skills" style template (blank Certificate ID/Support/
	        // Completion Date footer, blank name line, bracketed
	        // [Course Name]/[Course Type] placeholder paragraph) matching
	        // the admin-supplied reference layout. This is the artwork used
	        // for real, per-student generated certificates; the course-page
	        // marketing preview (CertificateSection.jsx) intentionally uses
	        // a different image (cer.png) and is not expected to match this
	        // one pixel-for-pixel.
	        PDDocument document = new PDDocument();

	        byte[] bgImageBytes;
	        try (java.io.InputStream in = new ClassPathResource("templates/Certificate_Genarated.jpg")
	                .getInputStream()) {
	            bgImageBytes = in.readAllBytes();
	        }
	        org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject bgImage = org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
	                .createFromByteArray(document, bgImageBytes, "Certificate_Genarated");

	        // Page sized to the background image's own aspect ratio
	        // (512x362px, ratio 1.4144 - very close to A4 landscape's
	        // 1.4151) so it fills edge-to-edge with no letterboxing/cropping,
	        // at a normal print-scale width (842pt ~= A4 landscape width).
	        float pageWidth = 842f;
	        float pageHeight = pageWidth / ((float) bgImage.getWidth() / bgImage.getHeight());
	        PDPage page = new PDPage(new PDRectangle(pageWidth, pageHeight));
	        document.addPage(page);

	        PDPageContentStream contentStream = new PDPageContentStream(document, page,
	                PDPageContentStream.AppendMode.APPEND, true, true);
	        contentStream.drawImage(bgImage, 0, 0, pageWidth, pageHeight);

	        PDFont fontBold = PDType1Font.HELVETICA_BOLD;
	        PDFont fontItalic = PDType1Font.HELVETICA;

	        // A semi-transparent cover was tried here to let the artwork's
	        // baked watermark show through the name/passage cover rects
	        // instead of being erased. Reverted: even at 97% opacity, the
	        // artwork's OWN baked placeholder text ("Student Name",
	        // "[Course Name]...") stayed faintly legible underneath the real
	        // text, reading as visible overlapping/duplicated content between
	        // the name and passage. Fully opaque avoids that outright, at
	        // the cost of the watermark not showing through in this area -
	        // an explicit tradeoff, not an oversight.
	        PDExtendedGraphicsState semiTransparentFill = new PDExtendedGraphicsState();
	        semiTransparentFill.setNonStrokingAlphaConstant(1f);
	        PDExtendedGraphicsState opaqueFill = new PDExtendedGraphicsState();
	        opaqueFill.setNonStrokingAlphaConstant(1f);

	        int nameFontSize = 24;
	        int aboutCourseFontSize = 12;

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
	            " Program conducted by Hachion, comprising " +
	            totalHours +
	            " hours of " + safeMode + ", and has demonstrated proficiency in core concepts " +
	            "and practical applications aligned with industry standards.";

	        // All X/Y fractions below were measured directly against the
	        // Certificate_Genarated.jpg artwork (top-left origin, as the image
	        // is normally viewed) via a labeled percentage-grid render, and
	        // converted to PDFBox's bottom-left origin via
	        // `pageHeight * (1 - topFractionFromTop)`. The image's own
	        // right-hand content column (the white certificate panel, right
	        // of the navy "Key Skills" sidebar + gold border) is horizontally
	        // centered at ~64% of the page width, not 50% - the sidebar
	        // shifts the true visual center right.
	        float centerXRatio = 0.64f;

	        // =======================
	        // 1) Student Name
	        // =======================
	        // This artwork's name line is blank (no baked placeholder text),
	        // but a cover rect is still drawn for safety/consistency in case
	        // of artwork variations. No separate "Student ID" line here - the
	        // Certificate ID in the footer below IS the student's Student ID.
	        float nameCoverTopY = pageHeight * (1 - 0.30f);
	        float nameCoverBottomY = pageHeight * (1 - 0.475f);
	        float nameCoverWidth = pageWidth * 0.55f;
	        float nameCoverX = (pageWidth * centerXRatio) - (nameCoverWidth / 2);
	        contentStream.setNonStrokingColor(1f, 1f, 1f);
	        contentStream.setGraphicsStateParameters(semiTransparentFill);
	        contentStream.addRect(nameCoverX, nameCoverBottomY, nameCoverWidth, nameCoverTopY - nameCoverBottomY);
	        contentStream.fill();
	        contentStream.setGraphicsStateParameters(opaqueFill);

	        float nameWidth = fontBold.getStringWidth(studentName) / 1000 * nameFontSize;
	        float nameAreaWidth = pageWidth * 0.50f;
	        while (nameWidth > nameAreaWidth && nameFontSize > 17) {
	            nameFontSize--;
	            nameWidth = fontBold.getStringWidth(studentName) / 1000 * nameFontSize;
	        }

	        contentStream.beginText();
	        contentStream.setFont(fontBold, nameFontSize);
	        contentStream.setNonStrokingColor(0.055f, 0.286f, 0.659f);
	        float nameX = (pageWidth * centerXRatio) - (nameWidth / 2);
	        contentStream.newLineAtOffset(nameX, pageHeight * (1 - 0.44f));
	        contentStream.showText(studentName);
	        contentStream.endText();

	        // =======================
	        // 2) About text (wrapped, bold parts) - covers the baked-in
	        // bracketed placeholder paragraph ("...successfully completed
	        // the [Course Name] Program..., comprising 30 hours of [Course
	        // Type]...") with white, then draws the real course/hours/mode
	        // paragraph in its place.
	        // =======================
	        float aboutCoverTopY = pageHeight * (1 - 0.495f);
	        float aboutCoverBottomY = pageHeight * (1 - 0.65f);
	        float aboutCoverWidth = pageWidth * 0.64f;
	        float aboutCoverX = (pageWidth * centerXRatio) - (aboutCoverWidth / 2);
	        contentStream.setNonStrokingColor(1f, 1f, 1f);
	        contentStream.setGraphicsStateParameters(semiTransparentFill);
	        contentStream.addRect(aboutCoverX, aboutCoverBottomY, aboutCoverWidth, aboutCoverTopY - aboutCoverBottomY);
	        contentStream.fill();
	        contentStream.setGraphicsStateParameters(opaqueFill);

	        float aboutTopY = pageHeight * (1 - 0.545f);
	        float availableAboutHeight = aboutTopY - aboutCoverBottomY - 6f;
	        float safeWhiteWidth = aboutCoverWidth - 10f;
	        float safeWhiteStartX = aboutCoverX + 5f;

	        String[] words = aboutText.split(" ");
	        List<String> lines;
	        float lineGap;

	        while (true) {
	            lines = new ArrayList<>();
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

	            lineGap = aboutCourseFontSize + 4;
	            float neededHeight = (lines.size() - 1) * lineGap;
	            if (neededHeight <= availableAboutHeight || aboutCourseFontSize <= 9) {
	                break;
	            }
	            aboutCourseFontSize--;
	        }

	        float startY = aboutTopY;
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

	            String remaining = line;

	            if (remaining.contains(courseName)) {
	                int index = remaining.indexOf(courseName);
	                String before = remaining.substring(0, index);
	                String after = remaining.substring(index + courseName.length());

	                contentStream.setFont(fontItalic, aboutCourseFontSize);
	                contentStream.showText(before);

	                contentStream.setFont(PDType1Font.HELVETICA_BOLD, aboutCourseFontSize);
	                contentStream.showText(courseName);

	                remaining = after;
	            }
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

	            contentStream.setFont(fontItalic, aboutCourseFontSize);
	            contentStream.showText(remaining);
	            contentStream.endText();
	        }

	        // =======================
	        // 2b) Key Skills sidebar - real tools/skills for this course
	        // (ToolsRepository, the same course_tools data the public course
	        // page's tools section and /api/tools/by-course serve), drawn
	        // white-on-navy into the template's left sidebar, below its own
	        // baked "Key Skills" heading (~54%-56%).
	        // =======================
	        List<ToolsEntity> toolsEntities = toolsRepository.findByCourseName(courseName);
	        List<String> skillNames = new ArrayList<>();
	        if (toolsEntities != null) {
	            for (ToolsEntity toolsEntity : toolsEntities) {
	                if (toolsEntity.getItems() == null) continue;
	                for (ToolsItemEntity item : toolsEntity.getItems()) {
	                    String skillName = sanitizeText(item.getToolsName());
	                    if (skillName != null && !skillName.isBlank()) {
	                        skillNames.add(skillName);
	                    }
	                }
	            }
	        }

	        if (!skillNames.isEmpty()) {
	            // The area below this artwork's "Key Skills" heading is
	            // already blank navy from the background JPG itself - no
	            // cover rectangle needed. An earlier cover-rect here used
	            // #16233E, visibly darker than the JPG's actual measured
	            // navy (#343F63), producing a visible seam/color clash
	            // against the reference design - removed rather than
	            // color-matched, since covering was never load-bearing.
	            float skillsCoverTopY = pageHeight * (1 - 0.565f);
	            float skillsCoverBottomY = pageHeight * (1 - 0.83f);

	            float skillFontSize = 14f;
	            float skillLineGap = 20f;
	            float skillX = pageWidth * 0.055f;
	            float skillY = skillsCoverTopY - (pageHeight * 0.035f);
	            float skillMinY = skillsCoverBottomY + 5f;

	            contentStream.setFont(fontItalic, skillFontSize);
	            contentStream.setNonStrokingColor(1f, 1f, 1f);
	            for (String skillName : skillNames) {
	                if (skillY < skillMinY) break;
	                contentStream.beginText();
	                contentStream.newLineAtOffset(skillX, skillY);
	                contentStream.showText(skillName);
	                contentStream.endText();
	                skillY -= skillLineGap;
	            }
	        }

	        // =======================
	        // 3) Footer: Certificate ID (= Student ID) + Support + Completion Date
	        // =======================
	        // This artwork bakes the footer as one label line ("Certificate
	        // ID:   Support: trainings@hachion.co   Completion Date:") with
	        // blank value slots, plus separate legal/footer text just below
	        // it. The label line is covered and all three fields - Certificate
	        // ID bottom-left, Support bottom-center, Completion Date
	        // bottom-right - are drawn fresh, without touching the legal text.
	        float footerCoverTopY = pageHeight * (1 - 0.885f);
	        float footerCoverBottomY = pageHeight * (1 - 0.925f);
	        // X nudged a few points right of the raw 0.30 fraction: at that
	        // exact fraction the cover's left edge lands inside the artwork's
	        // thin dark-navy accent stripe that borders the gold divider, so
	        // only the covered Y-range (footerCoverBottomY..Top) had that
	        // stripe erased - producing a visible stepped notch against the
	        // uncovered stripe just above/below it. Measured directly off the
	        // rendered pixels (pixel step ~5px at 150 DPI = ~2.4pt).
	        float footerCoverWidth = pageWidth * 0.67f - 3f;
	        float footerCoverX = pageWidth * 0.30f + 3f;
	        contentStream.setNonStrokingColor(1f, 1f, 1f);
	        contentStream.addRect(footerCoverX, footerCoverBottomY, footerCoverWidth, footerCoverTopY - footerCoverBottomY);
	        contentStream.fill();
	        // The small teal mark that appears just past this cover's right
	        // edge is the template's own bottom-right corner-bracket flourish
	        // (matching the one top-right) - legitimate artwork, not a stray
	        // baked-text sliver, so it's deliberately left uncovered.
	        float footerTextBaselineY = footerCoverBottomY + 3f;

	        LocalDate date2 = LocalDate.parse(completionDate);
	        String formattedDate2 = date2.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

	        // Certificate ID displayed to the learner is their own Student ID,
	        // not the DB-assigned sequential certificateId row number - the
	        // internal certificateId PK is still used for routing/filenames
	        // elsewhere, only the text drawn here changed. Sizes below are
	        // measured per-character from the HACH10000 reference (not
	        // eyeballed): label 14pt, ID value 12pt bold, Support 14pt,
	        // Completion Date 11pt bold throughout - the three fields are
	        // NOT centered/independently anchored in the reference, they are
	        // simply sequential with a small gap, which is what keeps them
	        // fitting the row; a fixed centered anchor for Support (an
	        // earlier version of this code) is what caused Completion Date
	        // to run off the page once sizes grew to match the reference.
	        String certIdLabel = "Certificate ID: ";
	        float certIdLabelSize = 14f;
	        float certIdValueSize = 12f;
	        float certIdStartX = pageWidth * 0.31f;
	        float certIdLabelWidth = fontItalic.getStringWidth(certIdLabel) / 1000 * certIdLabelSize;
	        float certIdValueWidth = fontBold.getStringWidth(studentId) / 1000 * certIdValueSize;
	        float certIdEndX = certIdStartX + certIdLabelWidth + certIdValueWidth;
	        contentStream.beginText();
	        contentStream.setFont(fontItalic, certIdLabelSize);
	        contentStream.setNonStrokingColor(0f, 0f, 0f);
	        contentStream.newLineAtOffset(certIdStartX, footerTextBaselineY);
	        contentStream.showText(certIdLabel);
	        contentStream.endText();
	        contentStream.beginText();
	        contentStream.setFont(fontBold, certIdValueSize);
	        contentStream.setNonStrokingColor(0f, 0f, 0f);
	        contentStream.newLineAtOffset(certIdStartX + certIdLabelWidth, footerTextBaselineY - 1f);
	        contentStream.showText(studentId);
	        contentStream.endText();

	        // Support: label + blue underlined email, styled like a
	        // hyperlink to match the reference layout. Always positioned
	        // right after Certificate ID's own end (whatever that is for
	        // this student's ID length) plus a fixed gap - sequential, not
	        // independently anchored. Label is regular weight (fontItalic),
	        // NOT bold - confirmed by zooming the reference render: "Support:"
	        // has the same thin stroke weight as "Certificate ID:", clearly
	        // lighter than the bold "HACH10000"/"Completion Date:" text next
	        // to it. Drawing it bold made it visually heavier/bigger than the
	        // reference at the same point size.
	        String supportLabel = "Support: ";
	        String supportEmail = "trainings@hachion.co";
	        float footerLabelSize = 14f;
	        float footerFieldGap = 20f;
	        float supportLabelWidth = fontItalic.getStringWidth(supportLabel) / 1000 * footerLabelSize;
	        float supportEmailWidth = fontItalic.getStringWidth(supportEmail) / 1000 * footerLabelSize;
	        float supportTotalWidth = supportLabelWidth + supportEmailWidth;
	        float supportStartX = certIdEndX + footerFieldGap;
	        contentStream.beginText();
	        contentStream.setFont(fontItalic, footerLabelSize);
	        contentStream.setNonStrokingColor(0f, 0f, 0f);
	        contentStream.newLineAtOffset(supportStartX, footerTextBaselineY);
	        contentStream.showText(supportLabel);
	        contentStream.endText();
	        // Measured directly from the HACH10000 reference render (#0189AD) -
	        // a distinct teal/link blue, not the same blue used for the name.
	        contentStream.beginText();
	        contentStream.setFont(fontItalic, footerLabelSize);
	        contentStream.setNonStrokingColor(0.004f, 0.537f, 0.678f);
	        contentStream.newLineAtOffset(supportStartX + supportLabelWidth, footerTextBaselineY);
	        contentStream.showText(supportEmail);
	        contentStream.endText();
	        contentStream.setStrokingColor(0.004f, 0.537f, 0.678f);
	        contentStream.setLineWidth(0.4f);
	        contentStream.moveTo(supportStartX + supportLabelWidth, footerTextBaselineY - 1.2f);
	        contentStream.lineTo(supportStartX + supportLabelWidth + supportEmailWidth, footerTextBaselineY - 1.2f);
	        contentStream.stroke();

	        // Completion Date: bold label + bold value, both 11pt per the
	        // reference measurement (smaller than the other two fields).
	        // Positioned right after Support's own end plus a fixed gap.
	        String dateLabel = "Completion Date: ";
	        float dateFontSize = 11f;
	        float dateLabelWidth = fontBold.getStringWidth(dateLabel) / 1000 * dateFontSize;
	        float supportEndX = supportStartX + supportTotalWidth;
	        float dateStartX = supportEndX + footerFieldGap;
	        contentStream.beginText();
	        contentStream.setFont(fontBold, dateFontSize);
	        contentStream.setNonStrokingColor(0f, 0f, 0f);
	        contentStream.newLineAtOffset(dateStartX, footerTextBaselineY);
	        contentStream.showText(dateLabel);
	        contentStream.endText();
	        contentStream.beginText();
	        contentStream.setFont(fontBold, dateFontSize);
	        contentStream.setNonStrokingColor(0f, 0f, 0f);
	        contentStream.newLineAtOffset(dateStartX + dateLabelWidth, footerTextBaselineY);
	        contentStream.showText(formattedDate2);
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
				// Certificate ID shown to the learner must equal their Student ID,
				// same convention as the PDF footer and the admin panel - not a
				// separately fabricated "CERT-<row id>" string.
				.map(r -> new CertificateDTO(r.getId(), r.getCourseName(), r.getGrade(), r.getIssueDate(),
						r.getStudentId(), r.getCertificatePath()))
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
