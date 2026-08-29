package com.booki.dto;

import com.booki.domain.Message;
import lombok.Data;

import java.time.Instant;

@Data
public class MessageResponse {
    private Long id;
    private String speaker;
    private String inputType;
    private String message;
    private Instant createdAt;

    public static MessageResponse of(Message message) {
        MessageResponse response = new MessageResponse();
        response.setId(message.getId());
        response.setSpeaker(message.getSpeaker().name());
        response.setInputType(message.getInputType().name());
        response.setMessage(message.getMessage());
        response.setCreatedAt(message.getCreatedAt());
        return response;
    }
}
