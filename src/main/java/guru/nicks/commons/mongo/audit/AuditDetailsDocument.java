package guru.nicks.commons.mongo.audit;

import guru.nicks.commons.log.domain.LogContext;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.PropertyUtils;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

/**
 * Audit details for MongoDB documents.
 */
@Document
@Data
@NoArgsConstructor
@AllArgsConstructor
//
@FieldNameConstants
@Builder
@Slf4j
public class AuditDetailsDocument implements Serializable {

    /**
     * Doesn't always correspond to an existing user because the user may have been deleted.
     */
    private String userId;

    /**
     * {@link LogContext#TRACE_ID}
     */
    private String traceId;

    /**
     * Assigns:
     * <ul>
     *     <li>{@link #getTraceId() traceId} from {@link LogContext#TRACE_ID}</li>
     *     <li>{@link #getUserId() userId} from the argument using reflection ({@code id} property)</li>
     * </ul>
     * <p>
     * {@link UserDetails} has no {@code id} property, but its subclasses may have. Exceptions (no such property /
     * error reading property / etc.) are logged ignored.
     *
     * @param userDetails user details
     */
    public AuditDetailsDocument(UserDetails userDetails) {
        traceId = LogContext.TRACE_ID.find().orElse(null);

        try {
            userId = Objects.toString(PropertyUtils.getProperty(userDetails, "id"), null);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            log.error("Failed to retrieve user ID (as 'id' property) from [{}], ignoring: {}",
                    userDetails.getClass().getName(), e.getMessage(), e);
        }
    }

}
