package org.aisa.api.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * The timestamps every table carries.
 *
 * <p>Filled by JPA auditing rather than by database defaults so that the value Hibernate
 * holds after a save matches the row — with only a DB-side {@code DEFAULT now()} the
 * in-memory entity keeps a null {@code updatedAt} until it is re-read, and the API returns
 * that null. The SQL defaults remain as a backstop for rows inserted by migrations.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class BaseEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
