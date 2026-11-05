package com.Bank_website.security;

import java.net.URI;

/**
 * Masking for Restricted/Confidential personal data. Apply before writing to a log or returning a value
 * in an API response that does not need the raw value. Every method is null-safe (null in, null out) and
 * never throws. Rules are documented in docs/DATA_GOVERNANCE.md (Deliverable 6).
 */
public final class MaskingUtil {

    private static final String STARS = "***";

    private MaskingUtil() {
    }

    /** +8801712345678 -> +8801712***678 ; 01712345678 -> 01712***678 */
    public static String maskMobile(String mobile) {
        if (mobile == null || mobile.isBlank()) {
            return mobile;
        }
        String m = mobile.replaceAll("[\\s-]", "");
        int head = m.startsWith("+880") ? 8 : m.startsWith("01") ? 5 : 3;
        if (m.length() <= head + 3) {
            return STARS;
        }
        return m.substring(0, head) + STARS + m.substring(m.length() - 3);
    }

    /** 1234567890 -> ****-****-7890 (national ID, passport, driving licence, birth certificate number) */
    public static String maskIdNumber(String idNumber) {
        if (idNumber == null || idNumber.isBlank()) {
            return idNumber;
        }
        String s = idNumber.replaceAll("[\\s-]", "");
        return s.length() <= 4 ? "****" : "****-****-" + s.substring(s.length() - 4);
    }

    /** Same rule as the ID number. */
    public static String maskDocumentNumber(String documentNumber) {
        return maskIdNumber(documentNumber);
    }

    /** rahim@example.com -> r***@example.com */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        int at = email.indexOf('@');
        if (at < 1) {
            return STARS;
        }
        return email.charAt(0) + STARS + email.substring(at);
    }

    /** 1992-04-10 -> 1992-**-** (keeps the year so age checks still work) */
    public static String maskDateOfBirth(String isoDate) {
        if (isoDate == null || isoDate.length() < 4 || !isoDate.substring(0, 4).chars().allMatch(Character::isDigit)) {
            return isoDate == null ? null : STARS;
        }
        return isoDate.substring(0, 4) + "-**-**";
    }

    /** Rahim -> R*** */
    public static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        return name.trim().charAt(0) + STARS;
    }

    /** "House 1, Road 2, Dhaka" -> "***, Dhaka" (only the last component, usually the city, survives) */
    public static String maskAddress(String address) {
        if (address == null || address.isBlank()) {
            return address;
        }
        int comma = address.lastIndexOf(',');
        return comma < 0 ? STARS : STARS + ", " + address.substring(comma + 1).trim();
    }

    /** ACC-0000000004 -> ACC-******0004 */
    public static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return accountNumber == null ? null : STARS;
        }
        int dash = accountNumber.indexOf('-');
        String prefix = dash > 0 ? accountNumber.substring(0, dash + 1) : "";
        String body = accountNumber.substring(prefix.length());
        if (body.length() <= 4) {
            return prefix + STARS;
        }
        return prefix + "*".repeat(body.length() - 4) + body.substring(body.length() - 4);
    }

    /** https://files.example.com/kyc/abc/front.jpg?sig=secret -> https://files.example.com/*** (drops path and signed query) */
    public static String maskImageUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        try {
            URI uri = URI.create(url.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return STARS;
            }
            return uri.getScheme() + "://" + uri.getHost() + "/" + STARS;
        } catch (IllegalArgumentException e) {
            return STARS;
        }
    }

    /** 203.0.113.45 -> 203.0.113.*** ; 2001:db8:85a3::8a2e -> 2001:db8:**** */
    public static String maskIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return ip;
        }
        if (ip.contains(":")) {
            String[] groups = ip.split(":");
            return groups.length < 2 ? STARS : groups[0] + ":" + groups[1] + ":****";
        }
        int lastDot = ip.lastIndexOf('.');
        return lastDot < 0 ? STARS : ip.substring(0, lastDot + 1) + STARS;
    }

    /** is_pep, sanctions_match, aml_risk_rating: never log or show the value. */
    public static String maskSensitiveFlag(Object value) {
        return value == null ? null : STARS;
    }

    /** Refresh tokens, JWT ids, API keys: abcdefghijkl -> abcd...kl */
    public static String maskToken(String token) {
        if (token == null || token.isBlank()) {
            return token;
        }
        return token.length() <= 8 ? STARS : token.substring(0, 4) + "..." + token.substring(token.length() - 2);
    }
}
