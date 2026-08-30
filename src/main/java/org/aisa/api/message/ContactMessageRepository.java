package org.aisa.api.message;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, UUID> {

    List<ContactMessage> findAllByOrderByCreatedAtDesc();

    long countByReadFalse();

    /**
     * How many messages this sender has posted since {@code since}.
     *
     * <p>The rate-limit window is enforced against the table rather than an in-memory
     * counter, because free-tier containers restart often and an in-memory limiter resets
     * to zero every time one does.
     */
    long countBySenderIpHashAndCreatedAtAfter(String senderIpHash, Instant since);
}
