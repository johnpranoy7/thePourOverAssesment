package com.thepourover.cdp.ai;

import com.thepourover.cdp.service.PiiService;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatService {

    private static final String SYSTEM = """
            You are the Growth analytics assistant for The Pour Over's mini CDP.
            Scope: subscriber metrics, acquisition, opens/engagement, web visits, app activity, webhooks.
            Refuse off-topic requests briefly; suggest one Growth question instead.

            - Today is 2026-09-28. Use tools for numbers; never invent totals or ask for PII (tools use subscriber_ref only).
            - Day windows need an explicit number. If vague ("recently", "a couple days"), ask once, then call tools.
            - New subscribers → countNewSubscribers(days) (signup within N days + linked web visit).
            - Source / open / cold → countOpenActivity. Most engaged → countMostEngaged.
            - Count questions: lead with the number; optional short sample table. List questions: lead with the table.
            """;

    private final ObjectProvider<ChatClient.Builder> chatClientBuilder;
    private final CdpTools cdpTools;
    private final PiiService piiService;
    private final ChatMemory chatMemory;

    public ChatService(
            ObjectProvider<ChatClient.Builder> chatClientBuilder,
            CdpTools cdpTools,
            PiiService piiService,
            ChatMemory chatMemory) {
        this.chatClientBuilder = chatClientBuilder;
        this.cdpTools = cdpTools;
        this.piiService = piiService;
        this.chatMemory = chatMemory;
    }

    public String chat(String sessionId, String conversationId, String message, boolean reset) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session required");
        }
        if (conversationId == null || conversationId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conversationId required");
        }

        // Isolate users (session) and tabs (conversationId)
        String memoryId = sessionId + "::" + conversationId.trim();
        if (reset) {
            chatMemory.clear(memoryId);
        }

        ChatClient.Builder builder = chatClientBuilder.getIfAvailable();
        if (builder == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SPRING_AI_OPENAI_API_KEY is not configured");
        }

        String safeMessage = piiService.redactEmailsInText(message);

        try {
            String answer = builder.build()
                    .prompt()
                    .system(SYSTEM)
                    .messages(safeConversationHistory(memoryId))
                    .user(safeMessage)
                    .tools(cdpTools)
                    .call()
                    .content();

            if (answer == null || answer.isBlank()) {
                answer = "No response.";
            }
            // Never persist or return emails the model invents/echoes.
            answer = piiService.redactEmailsInText(answer);

            chatMemory.add(memoryId, new UserMessage(safeMessage));
            chatMemory.add(memoryId, new AssistantMessage(answer));
            return answer;
        } catch (Exception e) {
            throw new ResponseStatusException(mapStatus(e), mapMessage(e));
        }
    }

    public void clear(String sessionId, String conversationId) {
        if (sessionId == null || conversationId == null || conversationId.isBlank()) {
            return;
        }
        chatMemory.clear(sessionId + "::" + conversationId.trim());
    }

    /** Prefer clean user/assistant turns; skip assistant tool-call shells in history. */
    private List<Message> safeConversationHistory(String memoryId) {
        return chatMemory.get(memoryId).stream()
                .filter(message -> message instanceof UserMessage
                        || (message instanceof AssistantMessage assistant && !assistant.hasToolCalls()))
                .toList();
    }

    private static HttpStatus mapStatus(Exception e) {
        String msg = fullMessage(e).toLowerCase();
        if (msg.contains("authentication") || msg.contains("unauthorized") || msg.contains("401")) {
            return HttpStatus.UNAUTHORIZED;
        }
        return HttpStatus.BAD_GATEWAY;
    }

    private static String mapMessage(Exception e) {
        String msg = fullMessage(e);
        if (msg.toLowerCase().contains("authentication")
                || msg.toLowerCase().contains("unauthorized")
                || msg.toLowerCase().contains("401")) {
            return "OpenAI rejected the API key. Set a valid SPRING_AI_OPENAI_API_KEY "
                    + "(sk-...) in your environment or IDE run config, then restart the backend.";
        }
        return "Assistant failed: " + e.getMessage();
    }

    private static String fullMessage(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = e;
        while (cur != null) {
            if (cur.getMessage() != null) {
                sb.append(cur.getMessage()).append(' ');
            }
            cur = cur.getCause();
        }
        return sb.toString();
    }
}
