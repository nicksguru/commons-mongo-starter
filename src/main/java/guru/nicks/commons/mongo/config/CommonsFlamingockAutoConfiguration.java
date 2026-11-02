package guru.nicks.commons.mongo.config;

import io.flamingock.api.annotations.TargetSystem;
import io.flamingock.store.mongodb.sync.MongoDBSyncAuditStore;
import io.flamingock.targetsystem.mongodb.springdata.MongoDBSpringDataTargetSystem;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Applications must enable Flamingock (so it applies migrations) by adding:
 * <code>@EnableFlamingock(stages = @Stage(location = "com.yourcompany.mongo.changes"))</code>.
 * <p>
 * WARNING: above, the stage location must be a literal package name, NOT a placeholder like {@code ${app.rootPackage}}:
 * the {@code flamingock-processor} bakes it into the generated metadata as-is, and the runner (with strict stage
 * mapping enabled) fails to map change units to stages when the placeholder is unresolved.
 * <p>
 * WARNING: Mongo migrations happen AFTER all beans have been initialized, therefore other beans should NOT rely on
 * migration results in their {@link PostConstruct @PostConstruct}.
 * <p>
 * Change units live must be named {@code _ORDER__CHANGE-NAME} - the order is extracted from the filename, see
 * <a href="https://docs.flamingock.io/changes/anatomy-and-structure">Anatomy &amp; Structure</a>. They must
 * never be renamed or edited once they've been applied somewhere - instead, create a new change unit.
 * <p>
 */
@AutoConfiguration
public class CommonsFlamingockAutoConfiguration {

    /**
     * ID of the Flamingock target system backed by the primary {@link MongoTemplate}. Also referenced by
     * {@link TargetSystem @TargetSystem} on change units.
     */
    public static final String DEFAULT_MONGO_TARGET_SYSTEM = "mongodb-target-system";

    /**
     * Flamingock target system applying DB migrations to the database of the {@link Primary @Primary} Mongo template.
     * Picked up by the Flamingock Spring Boot integration automatically.
     *
     * @see #DEFAULT_MONGO_TARGET_SYSTEM
     */
    @ConditionalOnMissingBean
    @Bean
    public MongoDBSpringDataTargetSystem mongoFlamingockTargetSystem(MongoTemplate mongoTemplate) {
        return new MongoDBSpringDataTargetSystem(DEFAULT_MONGO_TARGET_SYSTEM, mongoTemplate);
    }

    /**
     * Flamingock audit store. Despite its name, the 'sync' store accepts the Spring Data target system (both wrap the
     * same MongoClient and database) - see
     * <a href="https://docs.flamingock.io/audit-stores/community/mongodb-audit-store">MongoDB audit store</a>.
     */
    @ConditionalOnMissingBean
    @Bean
    public MongoDBSyncAuditStore mongoFlamingockAuditStore(MongoDBSpringDataTargetSystem mongoFlamingockTargetSystem) {
        return MongoDBSyncAuditStore.from(mongoFlamingockTargetSystem);
    }

}
