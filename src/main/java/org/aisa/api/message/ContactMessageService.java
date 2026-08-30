package org.aisa.api.message;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.aisa.api.common.NotFoundException;
import org.aisa.api.common.RateLimitedException;
import org.aisa.api.config.AisaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ContactMessageService {

    private static final Logger log = LoggerFactory.getLogger(ContactMessageService.class);
    private static final Duration RATE_WINDOW = Duration.ofHours(1);

    private final ContactMessageRepository messages;
    private final Clock clock;
    private final int rateLimitPerHour;
    private final byte[] hashSalt;

    public ContactMessageService(
            ContactMessageRepository messages, Clock clock, AisaProperties properties) {
        this.messages = messages;
        this.clock = clock;
        this.rateLimitPerHour = properties.contact().rateLimitPerHour();
        /*
         * The IP is stored hashed, so a message document never holds a raw address. That
         * only helps if the salt is secret: there are only 2^32 IPv4 addresses, so an
         * unsalted hash is reversible by anyone who can read the collection.
         */
        String salt = properties.contact().hashSalt();
        if (salt == null || salt.isBlank()) {
            log.warn("No CONTACT_HASH_SALT and no FIREBASE_SERVICE_ACCOUNT: contact-form IP "
                    + "hashes are unsalted and therefore reversible. Fine for local development, "
                    + "not for a deployment.");
            salt = "aisa-development-only";
        }
        this.hashSalt = salt.getBytes(StandardCharsets.UTF_8);
    }

    public record ContactRequest(
            @NotBlank(message = "Your name is required") @Size(max = 160) String name,
            @NotBlank(message = "Your email is required")
            @Email(message = "That does not look like an email address")
            @Size(max = 255) String email,
            @Size(max = 200) String subject,
            @NotBlank(message = "A message is required") @Size(max = 5000) String message,
            /**
             * The honeypot from the original form, kept because it is free and it works on
             * the naive bots that make up most of this traffic. A real person never sees
             * the field, so anything in it is a bot; the response is still a cheerful 202
             * so the bot has no signal that it was caught.
             */
            String website) {}

    public record MessageResponse(
            UUID id, String name, String email, String subject, String body,
            boolean read, Instant createdAt) {}

    /**
     * @return true if the message was stored, false if it was silently discarded as spam
     */
    public boolean submit(ContactRequest request, HttpServletRequest httpRequest) {
        if (request.website() != null && !request.website().isBlank()) {
            log.debug("Discarded a contact submission that filled the honeypot field");
            return false;
        }

        String ipHash = hashClientIp(httpRequest);
        Instant windowStart = clock.instant().minus(RATE_WINDOW);
        if (messages.countRecentFrom(ipHash, windowStart) >= rateLimitPerHour) {
            throw new RateLimitedException(
                    "You have sent several messages already. Please try again in an hour.");
        }

        /*
         * Stored raw, not HTML-escaped. The old page escaped on the way in, which meant a
         * message about "a < b" was permanently mangled in the database. Escaping is the
         * renderer's job, and React does it automatically on the way out.
         */
        ContactMessage message = new ContactMessage(
                request.name().trim(), request.email().trim().toLowerCase(), request.message().trim());
        message.setSubject(request.subject() == null || request.subject().isBlank()
                ? null : request.subject().trim());
        message.setSenderIpHash(ipHash);
        messages.save(message);
        return true;
    }

    public List<MessageResponse> findAll() {
        return messages.findAllNewestFirst().stream()
                .map(ContactMessageService::toResponse)
                .toList();
    }

    public long countUnread() {
        return messages.countUnread();
    }

    public MessageResponse markRead(UUID id, boolean read) {
        ContactMessage message = require(id);
        message.setRead(read);
        return toResponse(messages.save(message));
    }

    public void delete(UUID id) {
        require(id);
        messages.deleteById(id);
    }

    private ContactMessage require(UUID id) {
        return messages.findById(id).orElseThrow(() -> new NotFoundException("Message", id));
    }

    /**
     * Behind Render's proxy the socket address is the proxy, so the real client is the
     * first entry of X-Forwarded-For. Only the first is used: the rest are attacker-
     * controlled and trusting them would let anyone reset their own rate limit by adding
     * a header.
     */
    private String hashClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(hashSalt);
            return HexFormat.of().formatHex(digest.digest(ip.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is required of every JVM; unreachable in practice.
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static MessageResponse toResponse(ContactMessage m) {
        return new MessageResponse(
                m.getId(), m.getName(), m.getEmail(), m.getSubject(), m.getBody(),
                m.isRead(), m.getCreatedAt());
    }
}
