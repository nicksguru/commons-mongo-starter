package guru.nicks.commons.mongo.audit;

import jakarta.annotation.Nonnull;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

/**
 * Returns auditor for MongoDB documents - current user info.
 */
public class MongoAuditor implements AuditorAware<AuditDetailsDocument> {

    @Nonnull
    @Override
    public Optional<AuditDetailsDocument> getCurrentAuditor() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(Authentication::getPrincipal)
                .filter(UserDetails.class::isInstance)
                .map(UserDetails.class::cast)
                .map(AuditDetailsDocument::new);
    }

}
