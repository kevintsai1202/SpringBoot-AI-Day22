package com.example.ai.advistor;

import java.util.Map;

import org.springframework.ai.chat.client.AdvisedRequest;
import org.springframework.ai.chat.client.RequestResponseAdvisor;
import org.springframework.ai.chat.model.ChatResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TokenUsageLogAdvistor implements RequestResponseAdvisor {

	@Override
	public AdvisedRequest adviseRequest(AdvisedRequest request, Map<String, Object> context) {
		log.info("Chat ID:{} User Message:{}",context.get("chatId"), request.userText());
		return RequestResponseAdvisor.super.adviseRequest(request, context);
	}

	@Override
	public ChatResponse adviseResponse(ChatResponse response, Map<String, Object> context) {
		log.info("Chat ID:{} Assistant Message:{}",context.get("chatId"), response.getResult().getOutput().getContent());
		log.info("PromptTokens:{}",response.getMetadata().getUsage().getPromptTokens());
		log.info("GenerationTokens:{}",response.getMetadata().getUsage().getGenerationTokens());
		log.info("TotalTokens:{}",response.getMetadata().getUsage().getTotalTokens());
		return RequestResponseAdvisor.super.adviseResponse(response, context);
	}
}
