package com.Bank_website.Novo_Bank_Service_PLC.ledger.exception;

/** A dependency the transfer needs (Customer Service) is down or too slow; the transfer is NOT attempted. */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public ServiceUnavailableException(String message) {
        super(message);
    }
}
