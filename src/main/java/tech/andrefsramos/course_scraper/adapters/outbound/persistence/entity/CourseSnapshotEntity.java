package tech.andrefsramos.course_scraper.adapters.outbound.persistence.entity;

import jakarta.persistence.*;

import java.sql.Timestamp;
import java.util.Objects;

@Entity
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CourseEntity getCourse() {
        return course;
    }

    public void setCourse(CourseEntity course) {
        this.course = course;
    }

    public String getStatusText() {
        return statusText;
    }

    public void setStatusText(String statusText) {
        this.statusText = statusText;
    }

    public String getPriceText() {
        return priceText;
    }

    public void setPriceText(String priceText) {
        this.priceText = priceText;
    }

    public String getRawJson() {
        return rawJson;
    }

    public void setRawJson(String rawJson) {
        this.rawJson = rawJson;
    }

    public Timestamp getCollectedAt() {
        return collectedAt;
    }

    public void setCollectedAt(Timestamp collectedAt) {
        this.collectedAt = collectedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        CourseSnapshotEntity that = (CourseSnapshotEntity) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(course, that.course) &&
                Objects.equals(statusText, that.statusText) &&
                Objects.equals(priceText, that.priceText) &&
                Objects.equals(rawJson, that.rawJson) &&
                Objects.equals(collectedAt, that.collectedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                course,
                statusText,
                priceText,
                rawJson,
                collectedAt
        );
    }

    @Override
    public String toString() {
        return "CourseSnapshotEntity{" +
                "id=" + id +
                ", course=" + course +
                ", statusText='" + statusText + '\'' +
                ", priceText='" + priceText + '\'' +
                ", rawJson='" + rawJson + '\'' +
                ", collectedAt=" + collectedAt +
                '}';
    }
}
