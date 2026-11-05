package com.Bank_website.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/** SMTP settings come from MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD (Mailtrap by default). */
@Configuration
public class MailConfig {

    @Bean
    public JavaMailSender javaMailSender(@Value("${mail.host}") String host,
                                         @Value("${mail.port}") int port,
                                         @Value("${mail.username:}") String username,
                                         @Value("${mail.password:}") String password) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(!username.isBlank()));
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.connectiontimeout", "4000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");
        return sender;
    }
}
