package tech.andrefsramos.course_scraper.adapters.outbound.persistence.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@Entity
@EqualsAndHashCode
@ToString
@Table(name = "course_snapshot")
public class CourseSnapshotEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="course_id", nullable=false)
    private CourseEntity course;

    @Column(name="status_text", length=200) private String statusText;
    @Column(name="price_text", length=200)  private String priceText;

    @Lob @Column(name="raw_json") private String rawJson;

    @Column(name="collected_at", insertable=false, updatable=false)
    private java.sql.Timestamp collectedAt;
}
