package guru.nicks.commons.mongo.domain;

import guru.nicks.commons.mongo.MongoCascadeSave;
import guru.nicks.commons.mongo.audit.AuditableDocument;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * All indexes are created by DB migrations. See also crucial notes in {@link MongoCascadeSave @MongoCascadeSave}.
 */
@Document(SequenceDocument.MONGO_COLLECTION)
// goes to _class, excludes package name
@TypeAlias("sequence")
//
@Data
@NoArgsConstructor
@FieldNameConstants // for use in manually constructed queries
//
@Jacksonized
@SuperBuilder(toBuilder = true)
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class SequenceDocument extends AuditableDocument {

    public static final String MONGO_COLLECTION = "sequences";

    private long value;

}
