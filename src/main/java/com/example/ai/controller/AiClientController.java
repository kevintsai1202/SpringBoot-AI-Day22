package com.example.ai.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.ai.service.ChatService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AiClientController {
	private final ChatService chatService;
	
	@GetMapping("/memchat")
	public String chat(@RequestParam String chatId, @RequestParam String prompt) {
		return chatService.chat(chatId, prompt);
	}
}
