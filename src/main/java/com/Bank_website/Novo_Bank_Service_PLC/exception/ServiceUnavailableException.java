package com.Bank_website.Novo_Bank_Service_PLC.exception;

public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
