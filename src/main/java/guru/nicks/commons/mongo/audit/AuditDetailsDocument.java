package guru.nicks.commons.mongo.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
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
@Builder(toBuilder = true)
public class AuditDetailsDocument implements Serializable {

    /**
     * Doesn't always correspond to an existing user because the user may have been deleted. As the last resort, see
     * {@link #getUsername()}.
     */
    private String userId;

    /**
     * The last resort to keep track of changes if the user ({@link #getUserId()}) has been deleted.
     */
    private String username;

    /**
     * Retrieves user ID and name out of the given argument. The user ID is retrieved using reflection because
     * {@link UserDetails} has no such property.
     *
     * @param userDetails user details
     */
    public AuditDetailsDocument(UserDetails userDetails) {
        username = userDetails.getUsername();

        try {
            userId = Objects.toString(
                    PropertyUtils.getProperty(userDetails, "id"),
                    null);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            // do nothing
        }
    }

}
