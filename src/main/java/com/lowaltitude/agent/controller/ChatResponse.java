package com.lowaltitude.agent.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private boolean success;
    private String message;
    private String error;

    public static ChatResponse success(String message) {
        return new ChatResponse(true, message, null);
    }

    public static ChatResponse error(String error) {
        return new ChatResponse(false, null, error);
    }
}
