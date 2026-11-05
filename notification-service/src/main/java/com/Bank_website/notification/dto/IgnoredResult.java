package com.Bank_website.notification.dto;

/** Returned (HTTP 200) when a fanned-out event is not meant for this endpoint. */
public record IgnoredResult(boolean ignored, String reason) {
}
