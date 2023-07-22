package searchengine.model;

import com.sun.istack.NotNull;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Table(name = "lemma", indexes = {@Index(name = "lemma_index", columnList = "lemma, site_id", unique = true)})
@Getter
@Setter
public class LemmaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @NotNull
    private int id;

    @Column(name = "site_id")
    @NotNull
    private int siteId;

    @NotNull
    private String lemma;

    @NotNull
    private int frequency;
}
