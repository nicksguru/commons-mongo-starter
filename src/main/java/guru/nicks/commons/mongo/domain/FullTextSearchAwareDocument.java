package guru.nicks.commons.mongo.domain;

import guru.nicks.commons.mongo.audit.AuditableDocument;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.Nonnull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Language;

import java.util.List;
import java.util.function.Supplier;

/**
 * This class should've been declared {@code abstract}, but it can't because of Lombok restrictions. Nevertheless, it
 * has no Mongo collection declared because it's a base class for others that need ngram-based fuzzy full text search.
 */
@Data
@NoArgsConstructor
@FieldNameConstants
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public abstract class FullTextSearchAwareDocument extends AuditableDocument {

    /**
     * Assigned automatically by a Mongo save listener.
     * <p>
     * WARNING: this field is only updated when using Spring Data to save documents. Also, remember that in Mongo, max.
     * record size is 16Mb. A rough estimate is that 100 words yield 1000 ngrams.
     * <p>
     * This field is supposed to have a higher priority in terms of its search index weight - prefix ngrams are supposed
     * to be stored here, they're more relevant than infixes.
     */
    @ToString.Exclude
    private String fullTextSearchDataHighPriority;

    /**
     * Assigned automatically by a Mongo save listener.
     * <p>
     * Same as {@link #getFullTextSearchDataHighPriority()}, but a Mongo index spanning this field gives it a lower
     * weight.
     */
    @ToString.Exclude
    private String fullTextSearchDataLowPriority;

    /**
     * This field is special for MongoDB full text indexes. It specifies the language of this document (as per
     * {@link MongoSearchLanguage}), which affects such features as stemming and stop words. Must always be a string.
     */
    @Language
    private String documentLanguage;

    /**
     * Can't declare this method as abstract (see class-level comments for details). Therefore, this default
     * implementation throws {@link org.apache.commons.lang3.NotImplementedException}.
     *
     * @return search data suppliers, such as property getters ({@link Object#toString()} will be called on the values)
     */
    @JsonIgnore
    @Nonnull
    public abstract List<Supplier<Object>> getFullTextSearchDataSuppliers();

}
