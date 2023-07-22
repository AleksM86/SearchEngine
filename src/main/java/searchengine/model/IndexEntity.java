package searchengine.model;

import com.sun.istack.NotNull;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Getter
@Setter
@Table (name = "`index`", indexes = {@Index(name = "pageId_lemmaId_index", columnList = "page_id, lemma_id", unique = true)})
public class IndexEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @NotNull
    private int id;

    @Column(name = "page_id")
    @NotNull
    private int pageId;

    @Column(name = "lemma_id")
    @NotNull
    private int lemmaId;

    @Column(name = "`rank`")
    @NotNull
    private int rank;

}
