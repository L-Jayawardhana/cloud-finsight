package com.cloudfinsight.apiservice.controller;

import com.cloudfinsight.apiservice.dto.ChatRequestDto;
import com.cloudfinsight.apiservice.dto.ChatResponseDto;
import com.cloudfinsight.apiservice.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Conversational follow-up questions grounded in recommendation data")
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    @Operation(summary = "Ask a follow-up question about a VM's recommendation",
        description = "Grounds the answer in the VM's most recent recommendation data, " +
                      "including its utilisation statistics. Maintains up to 5 turns of " +
                      "conversation history in Redis, keyed by vmId. Returns 404 if the " +
                      "vmId doesn't exist; if the VM exists but has no recommendation yet, " +
                      "still answers, but says so honestly rather than inventing one.")
    public ResponseEntity<ChatResponseDto> chat(@RequestBody ChatRequestDto request) {
        return chatService.chat(request.vmId(), request.message())
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
