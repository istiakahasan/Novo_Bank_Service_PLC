package com.Bank_website.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class MaskingUtilTest {

    @Test
    void mobile() {
        assertEquals("+8801712***678", MaskingUtil.maskMobile("+8801712345678"));
        assertEquals("01712***678", MaskingUtil.maskMobile("01712345678"));
        assertEquals("+8801712***678", MaskingUtil.maskMobile("+880 1712-345678"));
        assertEquals("***", MaskingUtil.maskMobile("0171"));
        assertNull(MaskingUtil.maskMobile(null));
    }

    @Test
    void idNumber() {
        assertEquals("****-****-7890", MaskingUtil.maskIdNumber("1234567890"));
        assertEquals("****-****-6789", MaskingUtil.maskIdNumber("1990-1234-56789".replace("-", "")));
        assertEquals("****", MaskingUtil.maskIdNumber("123"));
        assertEquals("****-****-7890", MaskingUtil.maskDocumentNumber("AB1234567890"));
        assertNull(MaskingUtil.maskIdNumber(null));
    }

    @Test
    void email() {
        assertEquals("r***@example.com", MaskingUtil.maskEmail("rahim@example.com"));
        assertEquals("***", MaskingUtil.maskEmail("not-an-email"));
        assertNull(MaskingUtil.maskEmail(null));
    }

    @Test
    void otherFields() {
        assertEquals("1992-**-**", MaskingUtil.maskDateOfBirth("1992-04-10"));
        assertEquals("***", MaskingUtil.maskDateOfBirth("garbage"));
        assertEquals("R***", MaskingUtil.maskName("Rahim"));
        assertEquals("***, Dhaka", MaskingUtil.maskAddress("House 1, Road 2, Dhaka"));
        assertEquals("***", MaskingUtil.maskAddress("Dhaka"));
        assertEquals("ACC-******0004", MaskingUtil.maskAccountNumber("ACC-0000000004"));
        assertEquals("https://files.example.com/***", MaskingUtil.maskImageUrl("https://files.example.com/kyc/abc/front.jpg?sig=secret"));
        assertEquals("***", MaskingUtil.maskImageUrl("not a url"));
        assertEquals("203.0.113.***", MaskingUtil.maskIp("203.0.113.45"));
        assertEquals("2001:db8:****", MaskingUtil.maskIp("2001:db8:85a3::8a2e"));
        assertEquals("***", MaskingUtil.maskSensitiveFlag(true));
        assertEquals("abcd...kl", MaskingUtil.maskToken("abcdefghijkl"));
    }

    @Test
    void maskedValuesNeverContainTheMiddleOfTheOriginal() {
        assertFalse(MaskingUtil.maskMobile("+8801712345678").contains("345"));
        assertFalse(MaskingUtil.maskIdNumber("1234567890").contains("12345"));
        assertFalse(MaskingUtil.maskEmail("rahim@example.com").contains("ahim"));
    }
}
