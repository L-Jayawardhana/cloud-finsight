package com.cloudfinsight.apiservice.ai;

/** One stored message in a chat conversation. role is "user" or "assistant". */
public record ChatTurn(String role, String content) {
}
