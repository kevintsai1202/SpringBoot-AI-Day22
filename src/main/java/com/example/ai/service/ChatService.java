package com.example.ai.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import com.example.ai.advistor.MyVectorStoreChatMemoryAdvisor;
import com.example.ai.advistor.TokenUsageLogAdvistor;

import lombok.RequiredArgsConstructor;


@RequiredArgsConstructor
@Service
public class ChatService {
	private final ChatClient chatClient;
	private final VectorStore vectorStore;
	
	public String chat(String chatId, String userMessage) {	   	
		return this.chatClient.prompt()
		.advisors(new MyVectorStoreChatMemoryAdvisor(vectorStore, chatId, 100),  new TokenUsageLogAdvistor())
		.advisors(context -> context.param("chatId", chatId))
		.user(userMessage)
        .call().content();
	}
}
