package com.Bank_website.authserver.exception;

/** More than 10 login attempts from one IP in a minute: 429 with Retry-After. */
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super("Too many login attempts, slow down");
        this.retryAfterSeconds = Math.max(retryAfterSeconds, 1);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
