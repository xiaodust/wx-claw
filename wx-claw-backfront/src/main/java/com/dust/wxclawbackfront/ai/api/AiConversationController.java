package com.dust.wxclawbackfront.ai.api;

import com.dust.wxclawbackfront.ai.api.io.AiConversationCreateRequest;
import com.dust.wxclawbackfront.ai.api.io.AiConversationDTO;
import com.dust.wxclawbackfront.ai.api.io.AiMessageCreateRequest;
import com.dust.wxclawbackfront.ai.api.io.AiMessageDTO;
import com.dust.wxclawbackfront.ai.dao.entity.AiConversation;
import com.dust.wxclawbackfront.ai.dao.entity.AiMessage;
import com.dust.wxclawbackfront.ai.service.AiConversationCrudService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai/conversations")
public class AiConversationController {

    private final AiConversationCrudService crudService;

    public AiConversationController(AiConversationCrudService crudService) {
        this.crudService = crudService;
    }

    @PostMapping
    public ResponseEntity<AiConversationDTO> createConversation(@RequestBody AiConversationCreateRequest request) {
        if (request == null || request.getSessionId() == null || request.getSessionId().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        AiConversation conversation = crudService.createOrGetConversation(request.getSessionId().trim(), request.getUsername());
        return ResponseEntity.ok(toDto(conversation));
    }

    @PostMapping("/new")
    public ResponseEntity<AiConversationDTO> createNewConversation(@RequestParam(name = "username", required = false) String username) {
        AiConversation conversation = crudService.createNewConversation(username);
        return ResponseEntity.ok(toDto(conversation));
    }

    @GetMapping("/active")
    public ResponseEntity<AiConversationDTO> getActiveConversation(@RequestParam(name = "username") String username) {
        if (username == null || username.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        AiConversation conversation = crudService.getActiveConversation(username.trim());
        if (conversation == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toDto(conversation));
    }

    @GetMapping
    public ResponseEntity<List<AiConversationDTO>> listConversations(@RequestParam(name = "username", required = false) String username) {
        List<AiConversationDTO> list = crudService.listConversations(username).stream().map(AiConversationController::toDto).toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<AiConversationDTO> getConversation(@PathVariable("sessionId") String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        AiConversation conversation = crudService.getConversationBySessionId(sessionId.trim());
        if (conversation == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toDto(conversation));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteConversation(@PathVariable("sessionId") String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        crudService.deleteConversationBySessionId(sessionId.trim());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<List<AiMessageDTO>> listMessages(@PathVariable("sessionId") String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        List<AiMessageDTO> list = crudService.listMessages(sessionId.trim()).stream().map(AiConversationController::toDto).toList();
        return ResponseEntity.ok(list);
    }

    @PostMapping("/{sessionId}/messages")
    public ResponseEntity<AiMessageDTO> appendMessage(@PathVariable("sessionId") String sessionId, @RequestBody AiMessageCreateRequest request) {
        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (request == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        AiMessage message = crudService.appendMessage(
                sessionId.trim(),
                request.getMessageType(),
                request.getContent(),
                request.getReasoningContent(),
                request.getResponseTime(),
                request.getErrorMsg()
        );

        return ResponseEntity.ok(toDto(message));
    }

    private static AiConversationDTO toDto(AiConversation entity) {
        if (entity == null) {
            return null;
        }
        AiConversationDTO dto = new AiConversationDTO();
        dto.setId(entity.getId());
        dto.setSessionId(entity.getSessionId());
        dto.setUsername(entity.getUsername());
        dto.setActive(entity.getActive());
        dto.setMessageCount(entity.getMessageCount());
        dto.setLastMessageTime(entity.getLastMessageTime());
        dto.setCreatedTime(entity.getCreatedTime());
        dto.setUpdatedTime(entity.getUpdatedTime());
        return dto;
    }

    private static AiMessageDTO toDto(AiMessage entity) {
        if (entity == null) {
            return null;
        }
        AiMessageDTO dto = new AiMessageDTO();
        dto.setId(entity.getId());
        dto.setSessionId(entity.getSessionId());
        dto.setMessageType(entity.getMessageType());
        dto.setContent(entity.getContent());
        dto.setReasoningContent(entity.getReasoningContent());
        dto.setMessageSeq(entity.getMessageSeq());
        dto.setResponseTime(entity.getResponseTime());
        dto.setErrorMsg(entity.getErrorMsg());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }
}
