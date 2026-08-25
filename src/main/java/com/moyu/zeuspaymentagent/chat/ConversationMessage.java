package com.moyu.zeuspaymentagent.chat;

public record ConversationMessage(Role role, String content) {

    public enum Role {
        USER,
        ASSISTANT
    }
}

