package com.moyu.zeuspaymentagent.chat.model;

public record ConversationMessage(Role role, String content) {

    public enum Role {
        USER,
        ASSISTANT
    }
}

