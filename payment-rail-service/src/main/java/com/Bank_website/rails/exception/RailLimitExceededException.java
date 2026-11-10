package com.Bank_website.rails.exception;

public class RailLimitExceededException extends RuntimeException {
    public RailLimitExceededException(String message) {
        super(message);
    }
}
