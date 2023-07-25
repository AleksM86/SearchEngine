package searchengine.model;

import com.sun.istack.NotNull;
import lombok.Getter;
import lombok.Setter;
import javax.persistence.*;

@Entity
@Setter
@Getter
@Table(name = "page", indexes = {@Index(name = "path_index", columnList = "path, site_id", unique = true)})
public class PageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @NotNull
    private int id;

    @NotNull
    @Column(name = "site_id")
    private int siteId;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "site_id", insertable = false, updatable = false)
    private SiteEntity siteEntity;

    @NotNull
    private String path;

    @NotNull
    private int code;

    @NotNull
    @Column(columnDefinition = "mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci")
    private String content;

    public PageEntity(int siteId, String path, int code, String content) {
        this.siteId = siteId;
        this.path = path;
        this.code = code;
        this.content = content;
    }
    public PageEntity(){}
}
