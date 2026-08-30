package org.aisa.api.message;

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
@Getter
@Setter
@NoArgsConstructor
public class ContactMessage extends BaseEntity {

    private UUID id;

    private String name;

    private String email;

    private String subject;

    private String body;

    private boolean read;

    private String senderIpHash;

    public ContactMessage(String name, String email, String body) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.email = email;
        this.body = body;
    }
}
