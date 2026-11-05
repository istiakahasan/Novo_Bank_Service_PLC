package com.Bank_website.Novo_Bank_Service_PLC.ledger.exception;

public class KycNotVerifiedException extends RuntimeException {
    public KycNotVerifiedException(String message) {
        super(message);
    }
}
