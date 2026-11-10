package com.Bank_website.cards.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public final class CardNumberGenerator {

    private CardNumberGenerator() {
    }

    public static String generateLuhnCardNumber() {
        Random random = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder("4"); // Visa prefix
        for (int i = 0; i < 14; i++) {
            sb.append(random.nextInt(10));
        }

        int sum = 0;
        boolean alternate = true;
        for (int i = sb.length() - 1; i >= 0; i--) {
            int n = Character.getNumericValue(sb.charAt(i));
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n = (n % 10) + 1;
                }
            }
            sum += n;
            alternate = !alternate;
        }

        int checkDigit = (10 - (sum % 10)) % 10;
        sb.append(checkDigit);
        return sb.toString();
    }

    public static boolean isValidLuhn(String cardNumber) {
        if (cardNumber == null || cardNumber.length() != 16) {
            return false;
        }
        int sum = 0;
        boolean alternate = false;
        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int n = Character.getNumericValue(cardNumber.charAt(i));
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n = (n % 10) + 1;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
    }

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    public static String mask(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        String last4 = cardNumber.substring(cardNumber.length() - 4);
        return "****-****-****-" + last4;
    }
}
