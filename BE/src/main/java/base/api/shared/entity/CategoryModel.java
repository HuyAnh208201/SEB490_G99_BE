package base.api.shared.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "categories")
public class CategoryModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** When false, category is deactivated (soft-hidden); not hard-deleted. */
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    /**
     * Short shelf-life / hard-to-store goods: not held in central warehouse inventory;
     * WM selects suppliers for direct branch delivery.
     */
    @Column(name = "short_date", nullable = false)
    private Boolean shortDate = false;

    @ManyToOne
    @JoinColumn(name = "parent_id")
    private CategoryModel parentCategory;

    @OneToMany(mappedBy = "parentCategory")
    private Set<CategoryModel> children = new LinkedHashSet<>();
}
