package tech.andrefsramos.course_scraper.adapters.outbound.persistence.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.sql.Timestamp;
import java.time.LocalDate;

@Setter
@Getter
@EqualsAndHashCode
@ToString
@Entity @Table(name = "course")
public class CourseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_id", nullable = false)
    private PlatformEntity platform;

    @Column(name="external_id_hash", nullable=false, length=64, unique=true)
    private String externalIdHash;

    @Column(nullable=false, length=500)
    private String title;

    @Column(nullable=false, length=1000)
    private String url;

    private String provider;
    private String area;

    @Column(name = "notified_at")
    private Timestamp notifiedAt;

    @Column(name="free_flag", nullable=false)
    private Boolean freeFlag;

    @Column(name="start_date") private LocalDate startDate;
    @Column(name="end_date")   private LocalDate endDate;

    @Column(name="status_text", length=200) private String statusText;
    @Column(name="price_text", length=200)  private String priceText;

    @Column(name="created_at", insertable=false, updatable=false)
    private Timestamp createdAt;

    @Column(name="updated_at", insertable=false, updatable=false)
    private Timestamp updatedAt;
}
