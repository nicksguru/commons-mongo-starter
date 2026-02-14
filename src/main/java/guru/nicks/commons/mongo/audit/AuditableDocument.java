package guru.nicks.commons.mongo.audit;

import guru.nicks.commons.mongo.MongoCascadeSave;
import guru.nicks.commons.utils.UuidUtils;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.Persistable;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * MongoDB document which keeps track of who and when created/modified it. Unfortunately, it's impossible to introduce a
 * {@link Version @Version}-annotated property (optimistic locking to circumvent the 'lost updates' issue - overwriting
 * updates made by someone else concurrently) because in that case {@link MongoCascadeSave @MongoCascadeSave} causes
 * error: <i>Cannot save entity ID with version VER to collection COL. Has it been modified meanwhile?</i>. An implicit
 * modification takes place indeed: after implicit inserts,
 * <p>
 * WARNING: manually setting the primary key prevents Spring Data from setting
 * {@link #getCreatedDate()}/{@link #getCreatedBy()}.
 *
 * @param <T> {@code _id} column type, usually {@link ObjectId} or, if the creation timestamp should not be revealed,
 *            {@link UUID} (use {@link UuidUtils#generateUuidV4()} for fast generation)
 */
@Document
@Data
@NoArgsConstructor
@FieldNameConstants
@SuperBuilder(toBuilder = true)
public abstract class AuditableDocument<T> implements Persistable<T>, Serializable {

    /**
     * Primary ID. This field name is reserved by Mongo.
     * <p>
     * WARNING: all documents stored in the same collection must use the same Mongo's internal ID representation. If
     * some IDs are true strings (app-generated) and some are {@link org.bson.types.ObjectId} (Mongo-generated;
     * converted to and from {@link String} transparently, but the difference remains under the hood), such operators as
     * {@code lookup} will not work!
     */
    @Id
    private T id;

    /**
     * Date of record creation. Should not be declared with <code>@NotNull</code> because it's assigned automatically
     * upon entity validation.
     * <p>
     * WARNING: MongoDB doesn't support time zones - a custom converter must be installed for mapping to/from Date.
     */
    @CreatedDate
    private Instant createdDate;

    @CreatedBy
    private AuditDetailsDocument createdBy;

    /**
     * Date of record's last modification. Nullable because modification is NOT the same as creation.
     * <p>
     * WARNING: MongoDB doesn't support time zones - a custom converter must be installed for mapping to/from Date.
     */
    @LastModifiedDate
    private Instant lastModifiedDate;

    @LastModifiedBy
    private AuditDetailsDocument lastModifiedBy;

    /* COMMENTED OUT - see class-level comment for details
    @Version private Long version;*/

    @Override
    public boolean isNew() {
        return id == null;
    }

}
