package com.Bank_website.authserver.exception;

public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException() {
        super("Email is already registered");
    }
}
