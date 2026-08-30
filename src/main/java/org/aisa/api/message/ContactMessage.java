package org.aisa.api.message;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.aisa.api.common.BaseEntity;

/**
 * A submission from the public contact form.
 *
 * <p>Note what is <em>not</em> stored: the sender's IP. Only a salted hash of it is kept,
 * which is enough to count submissions from one source within the rate-limit window but
 * not enough to reconstruct a log of who visited the site. The hash is salted with the
 * JWT secret, so it is not reversible by rainbow table either.
 */
@Entity
@Table(name = "contact_message")
@Getter
@Setter
@NoArgsConstructor
public class ContactMessage extends BaseEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(length = 200)
    private String subject;

    @Column(nullable = false)
    private String body;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "sender_ip_hash", length = 64)
    private String senderIpHash;

    public ContactMessage(String name, String email, String body) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.email = email;
        this.body = body;
    }
}
