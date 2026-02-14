package guru.nicks.commons.mongo.domain;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Custom properties - Spring isn't aware of them. The idea is construct a connection URL out of them via placeholders.
 */
@ConfigurationProperties(prefix = "spring.data.mongodb.my")
@Validated
// immutability
@Value
@NonFinal // CGLIB creates a subclass to bind property values (nested classes don't need this)
@Builder(toBuilder = true)
public class MyMongoProperties {

    /**
     * mongodb / mongodb+srv
     */
    @NotBlank
    String scheme;

    /**
     * host1:port1,host2:port2, ...
     */
    @NotBlank
    String seedList;

    @NotBlank
    String database;

    /**
     * Technically, Mongo may work without any auth (during tests only!).
     */
    String username;

    /**
     * Technically, Mongo may work without any auth (during tests only!).
     */
    @ToString.Exclude
    String password;

    String options;

}
