package com.example.ai.advistor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.Content;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class MyVectorStoreChatMemoryAdvisor extends AbstractChatMemoryAdvisor<VectorStore> {
	private static final String DOCUMENT_METADATA_CONVERSATION_ID = "conversationId";

	private static final String DOCUMENT_METADATA_MESSAGE_TYPE = "messageType";

	private static final String DEFAULT_SYSTEM_TEXT_ADVISE = """

			Use the long term conversation memory from the LONG_TERM_MEMORY section to provide accurate answers.

			---------------------
			LONG_TERM_MEMORY:
			{long_term_memory}
			---------------------

			""";

	private final String systemTextAdvise;

	public MyVectorStoreChatMemoryAdvisor(VectorStore vectorStore) {
		this(vectorStore, DEFAULT_SYSTEM_TEXT_ADVISE);
	}

	public MyVectorStoreChatMemoryAdvisor(VectorStore vectorStore, String systemTextAdvise) {
		super(vectorStore);
		this.systemTextAdvise = systemTextAdvise;
	}

	public MyVectorStoreChatMemoryAdvisor(VectorStore vectorStore, String defaultConversationId,
			int chatHistoryWindowSize) {
		this(vectorStore, defaultConversationId, chatHistoryWindowSize, DEFAULT_SYSTEM_TEXT_ADVISE);
	}

	public MyVectorStoreChatMemoryAdvisor(VectorStore vectorStore, String defaultConversationId,
			int chatHistoryWindowSize, String systemTextAdvise) {
		super(vectorStore, defaultConversationId, chatHistoryWindowSize);
		this.systemTextAdvise = systemTextAdvise;
	}

	@Override
	public AdvisedRequest adviseRequest(AdvisedRequest request, Map<String, Object> context) {

		String advisedSystemText = request.systemText() + System.lineSeparator() + this.systemTextAdvise;

		var searchRequest = SearchRequest.query(request.userText())
			.withTopK(this.doGetChatMemoryRetrieveSize(context))
			.withFilterExpression(DOCUMENT_METADATA_CONVERSATION_ID + "== '" + this.doGetConversationId(context) +"'");

		List<Document> documents = this.getChatMemoryStore().similaritySearch(searchRequest);

		String longTermMemory = documents.stream()
			.map(Content::getContent).sorted().distinct()
			.collect(Collectors.joining(System.lineSeparator()));
		log.info(longTermMemory);

		Map<String, Object> advisedSystemParams = new HashMap<>(request.systemParams());
		advisedSystemParams.put("long_term_memory", longTermMemory);

		AdvisedRequest advisedRequest = AdvisedRequest.from(request)
			.withSystemText(advisedSystemText)
			.withSystemParams(advisedSystemParams)
			.build();

		UserMessage userMessage = new UserMessage(request.userText(), request.media());
		this.getChatMemoryStore().write(toDocuments(List.of(userMessage), this.doGetConversationId(context)));

		return advisedRequest;
	}

	@Override
	public ChatResponse adviseResponse(ChatResponse chatResponse, Map<String, Object> context) {

		List<Message> assistantMessages = chatResponse.getResults().stream().map(g -> (Message) g.getOutput()).toList();

		this.getChatMemoryStore().write(toDocuments(assistantMessages, this.doGetConversationId(context)));

		return chatResponse;
	}

	@Override
	public Flux<ChatResponse> adviseResponse(Flux<ChatResponse> fluxChatResponse, Map<String, Object> context) {

		return new MessageAggregator().aggregate(fluxChatResponse, chatResponse -> {
			List<Message> assistantMessages = chatResponse.getResults()
				.stream()
				.map(g -> (Message) g.getOutput())
				.toList();

			this.getChatMemoryStore().write(toDocuments(assistantMessages, this.doGetConversationId(context)));
		});
	}

	private List<Document> toDocuments(List<Message> messages, String conversationId) {

		List<Document> docs = messages.stream()
			.filter(m -> m.getMessageType() == MessageType.USER || m.getMessageType() == MessageType.ASSISTANT)
			.map(message -> {
				var metadata = new HashMap<>(message.getMetadata() != null ? message.getMetadata() : new HashMap<>());
				metadata.put(DOCUMENT_METADATA_CONVERSATION_ID, conversationId);
				metadata.put(DOCUMENT_METADATA_MESSAGE_TYPE, message.getMessageType().name());
				var doc = new Document(message.getContent(), metadata);
				return doc;
			})
			.toList();

		return docs;
	}
}
