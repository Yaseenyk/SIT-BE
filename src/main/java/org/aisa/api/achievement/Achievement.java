package org.aisa.api.achievement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.aisa.api.common.BaseEntity;

/**
 * Something a student won, published, or was selected for.
 *
 * <p>{@code student} is free text rather than a link to {@link org.aisa.api.member.Member}:
 * achievements belong to any student in the department, not only to office-bearers, and
 * the record should survive the person leaving the committee.
 */
@Entity
@Table(name = "achievement")
@Getter
@Setter
@NoArgsConstructor
public class Achievement extends BaseEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 160)
    private String student;

    @Column(length = 64)
    private String category;

    @Column(name = "achieved_on")
    private LocalDate achievedOn;

    @Column
    private String description;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(name = "photo_public_id", length = 255)
    private String photoPublicId;

    public Achievement(String title, String student) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.student = student;
    }
}
