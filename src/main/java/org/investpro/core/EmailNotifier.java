package org.investpro.core;

import lombok.extern.slf4j.Slf4j;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import java.util.Properties;

/**
 * Email notifier for InvestPro.

 * Supports:
 * - SMTP notifications
 * - plain text messages
 * - HTML messages
 * - NotificationMessage payloads

 * Environment variable fallback:
 * - INVESTPRO_EMAIL_FROM
 * - INVESTPRO_EMAIL_TO
 * - INVESTPRO_SMTP_HOST
 * - INVESTPRO_SMTP_PORT
 * - INVESTPRO_SMTP_USERNAME
 * - INVESTPRO_SMTP_PASSWORD
 * - INVESTPRO_SMTP_STARTTLS
 */
@Slf4j
public record EmailNotifier(
        String fromEmail,
        String toEmail,
        String smtpHost,
        int smtpPort,
        String username,
        String password,
        boolean startTls
) {
    private static final int DEFAULT_SMTP_PORT = 587;

    public EmailNotifier(String fromEmail, String toEmail) {
        this(
                fromEmail,
                toEmail,
                getenv("INVESTPRO_SMTP_HOST"),
                parsePort(getenv("INVESTPRO_SMTP_PORT")),
                getenv("INVESTPRO_SMTP_USERNAME"),
                getenv("INVESTPRO_SMTP_PASSWORD"),
                parseBoolean(getenv("INVESTPRO_SMTP_STARTTLS"))
        );
    }

    public EmailNotifier {
        fromEmail = firstNonBlank(fromEmail, getenv("INVESTPRO_EMAIL_FROM"));
        toEmail = firstNonBlank(toEmail, getenv("INVESTPRO_EMAIL_TO"));
        smtpHost = safe(smtpHost);
        smtpPort = smtpPort <= 0 ? DEFAULT_SMTP_PORT : smtpPort;
        username = safe(username);
        password = safe(password);
    }

    @Contract(" -> new")
    public static @NotNull EmailNotifier fromEnvironment() {
        return new EmailNotifier(
                getenv("INVESTPRO_EMAIL_FROM"),
                getenv("INVESTPRO_EMAIL_TO"),
                getenv("INVESTPRO_SMTP_HOST"),
                parsePort(getenv("INVESTPRO_SMTP_PORT")),
                getenv("INVESTPRO_SMTP_USERNAME"),
                getenv("INVESTPRO_SMTP_PASSWORD"),
                parseBoolean(getenv("INVESTPRO_SMTP_STARTTLS"))
        );
    }

    public boolean isEnabled() {
        return !fromEmail.isBlank()
                && !toEmail.isBlank()
                && !smtpHost.isBlank();
    }

    public boolean hasAuthentication() {
        return !username.isBlank() && !password.isBlank();
    }

    public boolean send(NotificationMessage message) {
        if (message == null) {
            return false;
        }

        return sendPlainText(
                message.title(),
                message.toPlainText()
        );
    }

    public boolean sendPlainText(String subject, String body) {
        if (!isEnabled()) {
            log.debug("Email notification skipped because EmailNotifier is not configured.");
            return false;
        }

        try {
            MimeMessage email = createBaseMessage(subject);
            email.setText(safe(body));

            Transport.send(email);

            log.info("EMAIL notification sent to={} from={} subject={}", toEmail, fromEmail, subject);
            return true;
        } catch (Exception exception) {
            log.warn("Email notification failed: {}", exception.getMessage(), exception);
            return false;
        }
    }

    public boolean sendHtml(String subject, String htmlBody) {
        if (!isEnabled()) {
            log.debug("HTML email notification skipped because EmailNotifier is not configured.");
            return false;
        }

        try {
            MimeMessage email = createBaseMessage(subject);
            email.setContent(safe(htmlBody), "text/html; charset=UTF-8");

            Transport.send(email);

            log.info("HTML EMAIL notification sent to={} from={} subject={}", toEmail, fromEmail, subject);
            return true;
        } catch (Exception exception) {
            log.warn("HTML email notification failed: {}", exception.getMessage(), exception);
            return false;
        }
    }

    public boolean sendSmartBotAlert(String title, String body) {
        NotificationMessage message = NotificationMessage.info(title, body).toEmailOnly();
        return send(message);
    }

    public boolean sendTradeAlert(String title, String body) {
        NotificationMessage message = NotificationMessage.trade(title, body).toEmailOnly();
        return send(message);
    }

    public boolean sendErrorAlert(String title, String body) {
        NotificationMessage message = NotificationMessage.error(title, body).toEmailOnly();
        return send(message);
    }

    private MimeMessage createBaseMessage(String subject) throws MessagingException {
        Session session = createSession();

        MimeMessage email = new MimeMessage(session);
        email.setFrom(new InternetAddress(fromEmail));
        email.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
        email.setSubject(safe(subject));
        email.setHeader("X-Mailer", "InvestPro SmartBot");
        email.setSentDate(new java.util.Date());

        return email;
    }

    private Session createSession() {
        Properties properties = new Properties();

        properties.put("mail.smtp.host", smtpHost);
        properties.put("mail.smtp.port", String.valueOf(smtpPort));
        properties.put("mail.smtp.auth", String.valueOf(hasAuthentication()));
        properties.put("mail.smtp.starttls.enable", String.valueOf(startTls));
        properties.put("mail.smtp.starttls.required", String.valueOf(startTls));
        properties.put("mail.smtp.connectiontimeout", "15000");
        properties.put("mail.smtp.timeout", "15000");
        properties.put("mail.smtp.writetimeout", "15000");

        if (!hasAuthentication()) {
            return Session.getInstance(properties);
        }

        return Session.getInstance(
                properties,
                new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password);
                    }
                }
        );
    }

    public @NotNull String configurationSummary() {
        return """
                EmailNotifier{
                    enabled=%s,
                    fromEmail='%s',
                    toEmail='%s',
                    smtpHost='%s',
                    smtpPort=%d,
                    auth=%s,
                    startTls=%s
                }
                """.formatted(
                isEnabled(),
                fromEmail,
                toEmail,
                smtpHost,
                smtpPort,
                hasAuthentication(),
                startTls
        );
    }

    private static String getenv(String key) {
        return safe(System.getenv(key));
    }

    public static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static @NotNull String firstNonBlank(String primary, String fallback) {
        String first = safe(primary);
        return first.isBlank() ? safe(fallback) : first;
    }

    private static int parsePort(String value) {
        try {
            return Integer.parseInt(safe(value));
        } catch (Exception ignored) {
            return EmailNotifier.DEFAULT_SMTP_PORT;
        }
    }

    private static boolean parseBoolean(String value) {
        String text = safe(value).toLowerCase();

        if (text.isBlank()) {
            return true;
        }

        return text.equals("true")
                || text.equals("1")
                || text.equals("yes")
                || text.equals("y");
    }
}