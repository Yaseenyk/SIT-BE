package org.aisa.api.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.aisa.api.committee.Committee;
import org.aisa.api.common.BaseEntity;

/**
 * A student office-bearer.
 *
 * <p>The committee is a real association. The Firestore version stored the committee's
 * display NAME on each member document and matched on that string, so renaming a committee
 * detached every member on it with no error anywhere.
 */
@Entity
@Table(name = "member")
@Getter
@Setter
@NoArgsConstructor
public class Member extends BaseEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 120)
    private String role;

    /**
     * Lazy, and always read through a fetch join in {@link MemberRepository}. The member
     * list renders the committee name for every row, so a lazy proxy resolved per row is
     * the N+1 this annotation pair exists to prevent.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "committee_id")
    private Committee committee;

    @Column(name = "academic_year", length = 32)
    private String academicYear;

    @Column(name = "linkedin_url", length = 500)
    private String linkedinUrl;

    @Column(name = "github_url", length = 500)
    private String githubUrl;

    @Column(length = 255)
    private String email;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(name = "photo_public_id", length = 255)
    private String photoPublicId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public Member(String name, String role) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.role = role;
    }
}
