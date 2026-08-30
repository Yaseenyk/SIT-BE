package org.aisa.api.achievement;

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
@Getter
@Setter
@NoArgsConstructor
public class Achievement extends BaseEntity {

    private UUID id;

    private String title;

    private String student;

    private String category;

    private LocalDate achievedOn;

    private String description;

    private String photoUrl;

    private String photoPublicId;

    public Achievement(String title, String student) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.student = student;
    }
}
