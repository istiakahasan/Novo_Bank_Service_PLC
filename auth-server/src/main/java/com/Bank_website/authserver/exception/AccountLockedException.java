package com.Bank_website.authserver.exception;

/** Account locked after too many failed logins: 423 Locked with a Retry-After header. */
public class AccountLockedException extends RuntimeException {

    private final long retryAfterSeconds;

    public AccountLockedException(long retryAfterSeconds) {
        super("Account is locked, try again later");
        this.retryAfterSeconds = Math.max(retryAfterSeconds, 1);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
