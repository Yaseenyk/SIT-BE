package org.aisa.api.committee;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.aisa.api.common.BaseEntity;

/**
 * A committee in the association structure — advisory, executive, or functional.
 *
 * <p>The id is the slug the public site links to ({@code #committee-technical}), so it is
 * chosen by the admin when the committee is created and never regenerated afterwards.
 */
@Entity
@Table(name = "committee")
@Getter
@Setter
@NoArgsConstructor
public class Committee extends BaseEntity {

    @Id
    @Setter(lombok.AccessLevel.NONE)
    @Column(length = 64)
    private String id;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false, length = 16)
    private String type;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 16)
    private String icon;

    /** A CSS gradient string. Presentation, but authored per committee by the admin. */
    @Column(length = 160)
    private String gradient;

    @Column(name = "size_label", length = 64)
    private String sizeLabel;

    @Column(length = 64)
    private String badge;

    @Column(name = "coord_label", length = 96)
    private String coordLabel;

    @Column(length = 160)
    private String coordinator;

    @Column(name = "coordinator_sub", length = 160)
    private String coordinatorSub;

    @Column(name = "coordinator_photo")
    private String coordinatorPhoto;

    @Column(name = "coordinator_photo_id", length = 255)
    private String coordinatorPhotoId;

    @Column(name = "coord2_label", length = 96)
    private String coord2Label;

    @Column(length = 160)
    private String coordinator2;

    @Column(name = "coordinator2_photo")
    private String coordinator2Photo;

    @Column(name = "coordinator2_photo_id", length = 255)
    private String coordinator2PhotoId;

    /**
     * The responsibilities bullets, in display order.
     *
     * <p>An {@code @ElementCollection} rather than a child entity: a bullet has no
     * identity of its own and is never referenced from anywhere else, so JPA can own the
     * rows entirely. Assigning a new list is enough to replace them all.
     *
     * <p>Eager because every read of a committee renders its bullets; lazy here would make
     * the structure page an N+1 query.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "committee_responsibility",
            joinColumns = @JoinColumn(name = "committee_id"))
    @OrderColumn(name = "position")
    @Column(name = "description", nullable = false)
    @Setter(lombok.AccessLevel.NONE)
    private List<String> responsibilities = new ArrayList<>();

    public Committee(String id) {
        this.id = id;
    }

    public void setResponsibilities(List<String> responsibilities) {
        // Mutated in place rather than reassigned: replacing the instance detaches the
        // collection Hibernate is tracking and throws on flush.
        this.responsibilities.clear();
        if (responsibilities != null) {
            this.responsibilities.addAll(responsibilities);
        }
    }
}
