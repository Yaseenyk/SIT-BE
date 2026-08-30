package org.aisa.api.committee;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.aisa.api.common.BaseEntity;

/**
 * A committee in the association structure — advisory, executive, or functional.
 *
 * <p>The id is the slug the public site links to ({@code #committee-technical}) and is also
 * the Firestore document id. It is chosen by the admin at creation and never changes.
 *
 * <p>That immutability is load-bearing. {@link org.aisa.api.member.Member} references a
 * committee by this id, and the original site's equivalent bug was referencing it by
 * display <em>name</em> — so renaming a committee silently detached every member on it.
 * Renaming {@code name} here touches nothing else.
 */
@Getter
@Setter
@NoArgsConstructor
public class Committee extends BaseEntity {

    /** Also the Firestore document id. Immutable once created — see CommitteeService. */
    @Setter(lombok.AccessLevel.NONE)
    private String id;

    private int displayOrder;

    private String type;

    private String name;

    private String icon;

    /** A CSS gradient string. Presentation, but authored per committee by the admin. */
    private String gradient;

    private String sizeLabel;

    private String badge;

    private String coordLabel;

    private String coordinator;

    private String coordinatorSub;

    private String coordinatorPhoto;

    private String coordinatorPhotoId;

    private String coord2Label;

    private String coordinator2;

    private String coordinator2Photo;

    private String coordinator2PhotoId;

    /**
     * The responsibilities bullets, in display order.
     *
     * <p>A Firestore array field. Order is preserved by the array itself, so the separate
     * position column the relational version needed is gone — this is the one place the
     * document model is a straightforwardly better fit.
     */
    @Setter(lombok.AccessLevel.NONE)
    private List<String> responsibilities = new ArrayList<>();

    public Committee(String id) {
        this.id = id;
    }

    public void setResponsibilities(List<String> responsibilities) {
        this.responsibilities.clear();
        if (responsibilities != null) {
            this.responsibilities.addAll(responsibilities);
        }
    }
}
