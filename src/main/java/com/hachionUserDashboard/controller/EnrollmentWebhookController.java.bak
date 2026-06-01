package com.hachionUserDashboard.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.hachionUserDashboard.dto.EnrollmentWebhookRequest;

@RestController
@RequestMapping("/api/webhook")
public class EnrollmentWebhookController {

	private final RestTemplate restTemplate = new RestTemplate();

	private static final String CHAT_WEBHOOK_URL = "https://chat.googleapis.com/v1/spaces/AAQAIuBdqEw/messages?key=AIzaSyDdI0hCZtE6vySjMm-WEfRq3CPzqKqqsHI&token=PgRWqYKF7NEeEAZzBWqT1iCcinleNzcwjmE4Gi0loSw";
	
	@PostMapping("/enrollment")
	public ResponseEntity<?> sendToChat(@RequestBody EnrollmentWebhookRequest request) {
		
		String message = """
				📢 *New Enquiry Form From Popup*

				👤 Name: %s
				📧 Email: %s
				📞 Phone: %s
				🎓 Course: %s
				📝 Remark: %s
				⏰ Time: %s
				""".formatted(request.getName(), request.getEmail(), request.getPhone(), request.getCourseName(),
				request.getRemark(), request.getTimestamp());

		
		Map<String, String> payload = new HashMap<>();
		payload.put("text", message);

		try {
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);

			HttpEntity<Map<String, String>> entity = new HttpEntity<>(payload, headers);

			restTemplate.postForEntity(CHAT_WEBHOOK_URL, entity, String.class);

			return ResponseEntity.ok(Map.of("status", "success", "message", "Sent to chat successfully"));

		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("status", "error", "message", "Failed to send to chat"));
		}
	}
}