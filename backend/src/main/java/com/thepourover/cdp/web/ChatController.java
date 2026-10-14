package com.thepourover.cdp.web;

import com.thepourover.cdp.ai.ChatService;
import com.thepourover.cdp.web.dto.Dtos.ChatRequest;
import com.thepourover.cdp.web.dto.Dtos.ChatResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request, HttpServletRequest httpRequest) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message required");
        }
        HttpSession session = httpRequest.getSession(true);
        String conversationId = request.conversationId();
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = UUID.randomUUID().toString();
        }
        boolean reset = Boolean.TRUE.equals(request.reset());
        String answer = chatService.chat(
                session.getId(),
                conversationId,
                request.message().trim(),
                reset);
        return new ChatResponse(answer, conversationId);
    }

    @DeleteMapping
    public Map<String, Object> clear(
            @RequestParam String conversationId,
            HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            chatService.clear(session.getId(), conversationId);
        }
        return Map.of("ok", true, "conversationId", conversationId);
    }
}
