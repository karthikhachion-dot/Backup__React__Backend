package com.hachionUserDashboard.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.hachionUserDashboard.dto.AskQueryWebhookRequest;
import com.hachionUserDashboard.service.WebhookSenderService;

@RestController
@RequestMapping("/ask-query")
public class AskQueryWebhookController {

	@Autowired
	private WebhookSenderService webhookService;

	@PostMapping("/send-to-webhook")
	public ResponseEntity<?> sendToWebhook(@RequestBody AskQueryWebhookRequest request) {
		webhookService.sendToWebhook(request);
		return ResponseEntity.ok().body("Sent to webhook successfully");
	}
}