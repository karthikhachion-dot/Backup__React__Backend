package com.hachionUserDashboard.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hachionUserDashboard.entity.Enroll;
import com.hachionUserDashboard.entity.RequestBatch;
import com.hachionUserDashboard.repository.CourseRepository;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

@Service
public class WhatsAppService {

	@Value("${twilio.accountSid}")
	private String accountSid;

	@Value("${twilio.authToken}")
	private String authToken;

	@Value("${twilio.fromWhatsApp}")
	private String fromWhatsApp;

	@Value("${twilio.contentSid}")
	private String contentSid;

	@Value("${twilio.demoContentSid}")
	private String demoContentSid;

	@Value("${twilio.fromSms}")
	private String fromSms;

	@Value("${twilio.reminderMessageBefore2Days:}")
	private String reminderMessageBefore2Days;

	@Value("${twilio.paymentDueToday:}")
	private String paymentDueToday;

	@Value("${twilio.paymentOverdue2Days:}")
	private String paymentOverdue2Days;

	@Value("${twilio.paymentOverdue7Days:}")
	private String paymentOverdue7Days;

	@Value("${twilio.selfPacedContentSid}")
	private String selfPacedContentSid;

	@Value("${twilio.mentoringModeContentSid}")
	private String mentoringModeContentSid;

	@Value("${twilio.enquiryContentSid}")
	private String enquiryContentSid;

	@Autowired
	private CourseRepository courseRepository;

	public void sendLiveClassDemoEnrollmentDetails(Enroll enroll) {
		try {

			Twilio.init(accountSid, authToken);

			String toWhatsApp = "whatsapp:" + enroll.getMobile().trim().replaceAll("\\s+", "");

			String formattedDate = enroll.getEnroll_date();
			try {
				LocalDate date = LocalDate.parse(enroll.getEnroll_date());
				formattedDate = date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));

			} catch (Exception ex) {

			}

			String candidateName = safe(enroll.getName(), "Student");
			String mode = safe(enroll.getMode(), "No");
			String demoDate = safe(formattedDate, "TBD");
			String time = safe(enroll.getTime(), "TBD");
			String meetingLink = safe(enroll.getMeeting_link(), "https://www.hachion.co");
			String trainerName = safe(enroll.getTrainer(), "Trainer");
			String topicsCovered = "Introduction to " + enroll.getCourse_name() + " & Career Path";

			String variablesJson = new ObjectMapper().writeValueAsString(Map.of(

					"1", candidateName, "2", mode, "3", demoDate, "4", time, "5", meetingLink, "6", trainerName, "7",
					topicsCovered));

			Message message = Message.creator(new PhoneNumber(toWhatsApp), new PhoneNumber(fromWhatsApp), "")
					.setContentSid(contentSid).setContentVariables(variablesJson).create();

		} catch (Exception e) {
			System.err.println("❌ Error sending WhatsApp message:");
			e.printStackTrace();
		}
	}

	private String safe(String value, String fallback) {
		return (value != null && !value.trim().isEmpty()) ? value.trim() : fallback;
	}

	public void sendLiveDemoEnrollmentDetails(Enroll enroll) {
		try {

			Twilio.init(accountSid, authToken);

			String toWhatsApp = "whatsapp:" + enroll.getMobile().trim().replaceAll("\\s+", "");

			String formattedDate = enroll.getEnroll_date();
			try {
				LocalDate date = LocalDate.parse(enroll.getEnroll_date());
				formattedDate = date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));

			} catch (Exception ex) {

			}

			String candidateName = safe(enroll.getName(), "Student");
			String demoDate = safe(formattedDate, "TBD");
			String time = safe(enroll.getTime(), "TBD");
			String meetingLink = safe(enroll.getMeeting_link(), "https://www.hachion.co");
			String trainerName = safe(enroll.getTrainer(), "Trainer");
			String topicsCovered = "Introduction to " + enroll.getCourse_name() + " & Career Path";

			String variablesJson = new ObjectMapper().writeValueAsString(Map.of(

					"1", candidateName, "2", demoDate, "3", time, "4", meetingLink, "5", trainerName, "6",
					topicsCovered));

			Message message = Message.creator(new PhoneNumber(toWhatsApp), new PhoneNumber(fromWhatsApp), "")
					.setContentSid(demoContentSid).setContentVariables(variablesJson).create();

		} catch (Exception e) {
			System.err.println("❌ Error sending WhatsApp message:");
			e.printStackTrace();
		}
	}

	public void sendEnrollmentSms(Enroll enroll) {
		try {
			Twilio.init(accountSid, authToken);

			String toMobile = enroll.getMobile().trim().replaceAll("\\s+", "");
			if (!toMobile.startsWith("+")) {
				toMobile = "+91" + toMobile;
			}

			String candidateName = safe(enroll.getName(), "Student");
			String formattedDate = safe(enroll.getEnroll_date(), "TBD");
			try {
				LocalDate date = LocalDate.parse(enroll.getEnroll_date());
				formattedDate = date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
			} catch (Exception ignored) {
			}

			String time = safe(enroll.getTime(), "TBD");
			String meetingLink = safe(enroll.getMeeting_link(), "https://www.hachion.co");
			String trainerName = safe(enroll.getTrainer(), "Trainer");
			String topicsCovered = "Introduction to " + safe(enroll.getCourse_name(), "the course") + " & Career Path";

			String messageBody = String.format(
					"Hello %s,\n\nYou are registered for a live demo session.\n\n📅 Date: %s\n🕒 Time: %s\n👨‍🏫 Trainer: %s\n📚 Topics: %s\n🔗 Link: %s\n\nSee you there!",
					candidateName, formattedDate, time, trainerName, topicsCovered, meetingLink);

			Message message = Message.creator(new PhoneNumber(toMobile), new PhoneNumber(fromSms), messageBody)
					.create();

			System.out.println("✅ SMS sent. SID: " + message.getSid());

		} catch (Exception e) {
			System.err.println("❌ Error sending enrollment SMS:");
			e.printStackTrace();
		}
	}

	public void sendTwoDayHeadsUpReminder(String mobile, String studentName, Double amountDue, String courseName,
			LocalDate dueDate) {
		try {
			Twilio.init(accountSid, authToken);

			String toWhatsApp = "whatsapp:" + (mobile == null ? "" : mobile.trim().replaceAll("\\s+", ""));
			if (toWhatsApp.length() <= "whatsapp:".length())
				return;

			if (reminderMessageBefore2Days == null || reminderMessageBefore2Days.isBlank()) {
				System.err
						.println("❌ WhatsApp D-2 template SID is not configured (twilio.reminderMessageBefore2Days).");
				return;
			}

			String variablesJson = new com.fasterxml.jackson.databind.ObjectMapper()
					.writeValueAsString(java.util.Map.of("1", safe(studentName, "Student"), "2", fmtAmount(amountDue),
							"3", safe(courseName, "Course"), "4", safe(fmtDate(dueDate, "dd-MMM-yyyy"), "TBD")));

			com.twilio.rest.api.v2010.account.Message.creator(new com.twilio.type.PhoneNumber(toWhatsApp),
					new com.twilio.type.PhoneNumber(fromWhatsApp), "").setContentSid(reminderMessageBefore2Days)
					.setContentVariables(variablesJson).create();

		} catch (Exception e) {
			System.err.println("❌ WhatsApp D-2 reminder failed: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private String fmtAmount(Double v) {
		if (v == null)
			return "0.00";
		return String.format("%.2f", v);
	}

	private String fmtDate(LocalDate d, String pattern) {
		try {
			return (d == null) ? null : d.format(java.time.format.DateTimeFormatter.ofPattern(pattern));
		} catch (Exception e) {
			return null;
		}
	}

	private String computeStatusText(LocalDate dueDate, LocalDate today, long daysOverdue) {
		if (dueDate == null || today == null)
			return "Payment status update";
		if (dueDate.isEqual(today))
			return "Payment due today";
		if (dueDate.isAfter(today)) {
			long d = java.time.temporal.ChronoUnit.DAYS.between(today, dueDate);
			return (d == 2) ? "Heads-up: due in 2 days" : "Upcoming payment";
		}
		return (daysOverdue >= 7) ? ("Overdue by " + daysOverdue + " days (final reminder)")
				: ("Overdue by " + daysOverdue + " days");
	}

	public void sendPaymentDueTodayReminder(String mobile, String studentName, Double amountDue, String courseName,
			LocalDate dueDate) {
		try {
			Twilio.init(accountSid, authToken);

			String toWhatsApp = "whatsapp:" + (mobile == null ? "" : mobile.trim().replaceAll("\\s+", ""));
			if (toWhatsApp.length() <= "whatsapp:".length())
				return; 

			if (paymentDueToday == null || paymentDueToday.isBlank()) {
				System.err.println("❌ Missing SID: set twilio.paymentDueToday in application.properties");
				return;
			}

			String variablesJson = new com.fasterxml.jackson.databind.ObjectMapper()
					.writeValueAsString(java.util.Map.of("1", safe(studentName, "Student"), "2", fmtAmount(amountDue),
							"3", safe(courseName, "Course"), "4", safe(fmtDate(dueDate, "dd-MMM-yyyy"), "TBD")));

			com.twilio.rest.api.v2010.account.Message.creator(new com.twilio.type.PhoneNumber(toWhatsApp),
					new com.twilio.type.PhoneNumber(fromWhatsApp), "").setContentSid(paymentDueToday)
					.setContentVariables(variablesJson).create();

		} catch (Exception e) {
			System.err.println("❌ WhatsApp D-0 reminder failed: " + e.getMessage());
			e.printStackTrace();
		}
	}

	public void sendPaymentOverdue2DaysReminder(String mobile, String studentName, Double amountDue, String courseName,
			LocalDate dueDate) {
		try {
			Twilio.init(accountSid, authToken);

			String toWhatsApp = "whatsapp:" + (mobile == null ? "" : mobile.trim().replaceAll("\\s+", ""));
			if (toWhatsApp.length() <= "whatsapp:".length())
				return;

			if (paymentOverdue2Days == null || paymentOverdue2Days.isBlank()) {
				System.err.println("❌ Missing SID: set twilio.paymentOverdue2Days in application.properties");
				return;
			}

			String variablesJson = new com.fasterxml.jackson.databind.ObjectMapper()
					.writeValueAsString(java.util.Map.of("1", safe(studentName, "Student"), "2", fmtAmount(amountDue),
							"3", safe(courseName, "Course"), "4", safe(fmtDate(dueDate, "dd-MMM-yyyy"), "TBD")));

			com.twilio.rest.api.v2010.account.Message.creator(new com.twilio.type.PhoneNumber(toWhatsApp),
					new com.twilio.type.PhoneNumber(fromWhatsApp), "" 
			).setContentSid(paymentOverdue2Days).setContentVariables(variablesJson).create();

		} catch (Exception e) {
			System.err.println("❌ WhatsApp Overdue(2d) send failed: " + e.getMessage());
			e.printStackTrace();
		}
	}

	public void sendPaymentOverdue7DaysReminder(String mobile, String studentName, Double amountDue, 
			String courseName, LocalDate dueDate) {
		try {
			Twilio.init(accountSid, authToken);

			String toWhatsApp = "whatsapp:" + (mobile == null ? "" : mobile.trim().replaceAll("\\s+", ""));
			if (toWhatsApp.length() <= "whatsapp:".length())
				return; 

			if (paymentOverdue7Days == null || paymentOverdue7Days.isBlank()) {
				System.err.println("❌ Missing SID: set twilio.paymentOverdue7Days in application.properties");
				return;
			}

			
			String variablesJson = new com.fasterxml.jackson.databind.ObjectMapper()
					.writeValueAsString(java.util.Map.of("1", safe(studentName, "Student"), "2", fmtAmount(amountDue),
							"3", safe(courseName, "Course"), "4", safe(fmtDate(dueDate, "dd-MMM-yyyy"), "TBD")));

			com.twilio.rest.api.v2010.account.Message.creator(new com.twilio.type.PhoneNumber(toWhatsApp),
					new com.twilio.type.PhoneNumber(fromWhatsApp), "" 
			).setContentSid(paymentOverdue7Days).setContentVariables(variablesJson).create();

		} catch (Exception e) {
			System.err.println("❌ WhatsApp Overdue(7d) send failed: " + e.getMessage());
			e.printStackTrace();
		}
	}

	public void sendSelfPacedEnrollmentConfirmed(Enroll enroll) {
		try {
			Twilio.init(accountSid, authToken);

			String toWhatsApp = "whatsapp:" + enroll.getMobile().trim().replaceAll("\\s+", "");
			if (toWhatsApp.length() <= "whatsapp:".length())
				return;

			String studentName = safe(enroll.getName(), "Student");
			String courseName = safe(enroll.getCourse_name(), "Course");

			String accessDate = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));

			String loginLink = "https://hachion.co";

			String variablesJson = new ObjectMapper().writeValueAsString(Map.of("1", studentName, 
					"2", courseName, 
					"3", accessDate, 
					"4", loginLink 
			));

			Message.creator(new PhoneNumber(toWhatsApp), new PhoneNumber(fromWhatsApp), "" 
			).setContentSid(selfPacedContentSid).setContentVariables(variablesJson).create();

			System.out.println("✅ Self-paced WhatsApp sent to " + toWhatsApp);

		} catch (Exception e) {
			System.err.println("❌ Error sending self-paced WhatsApp message:");
			e.printStackTrace();
		}
	}

	public void sendEnrollmentConfirmedUtility(RequestBatch requestBatch) {
		try {
			Twilio.init(accountSid, authToken);

			String toWhatsApp = "whatsapp:" + requestBatch.getMobile().trim().replaceAll("\\s+", "");
			if (toWhatsApp.length() <= "whatsapp:".length())
				return;

			
			String duration = "TBD";
			if (requestBatch.getCourseName() != null && !requestBatch.getCourseName().isBlank()) {
				duration = courseRepository.findNumberOfClassesByCourseName(requestBatch.getCourseName()).orElse("TBD");
			}

			String variablesJson = new ObjectMapper()
					.writeValueAsString(Map.of("1", "🎓 " + safe(requestBatch.getUserName(), "Student"), "2",
							"🧑‍🏫 " + safe(requestBatch.getMode(), "Mentoring Mode"), "3",
							"📘 " + safe(requestBatch.getCourseName(), "Course"), "4",
							"📅 " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy")), "5",
							"⏱️ " + safe(duration, "TBD"), "6",
							"🚀 " + safe(requestBatch.getMode(), "Instructor-Guided"), "7", "🌐 https://hachion.co"));

			Message.creator(new PhoneNumber(toWhatsApp), new PhoneNumber(fromWhatsApp), "")
					.setContentSid(mentoringModeContentSid).setContentVariables(variablesJson).create();

			System.out.println("✅ WhatsApp enrollment utility sent to " + toWhatsApp);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void sendEnquiryWhatsApp(String mobile, String studentName, String courseName) {
		try {

			Twilio.init(accountSid, authToken);

			String toWhatsApp = "whatsapp:" + mobile.trim().replaceAll("\\s+", "");

			String safeName = (studentName != null && !studentName.isEmpty()) ? "*" + studentName + "* 👋"
					: "*Student* 👋";

			String safeCourse = (courseName != null && !courseName.isEmpty()) ? "*" + courseName + "* 📚"
					: "*your selected course* 📚";

			String variablesJson = new ObjectMapper().writeValueAsString(Map.of("1", safeName, "2", safeCourse));

			Message message = Message.creator(new PhoneNumber(toWhatsApp), new PhoneNumber(fromWhatsApp), "")
					.setContentSid(enquiryContentSid).setContentVariables(variablesJson).create();

			System.out.println("✅ WhatsApp enquiry message sent to: " + toWhatsApp);

		} catch (Exception e) {
			System.err.println("❌ Error sending WhatsApp enquiry message:");
			e.printStackTrace();
		}
	}
}
