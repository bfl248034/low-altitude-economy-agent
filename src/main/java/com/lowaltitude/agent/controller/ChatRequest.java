package com.lowaltitude.agent.controller;

import lombok.Data;

@Data
public class ChatRequest {
    private String message;
    private String sessionId;
}
