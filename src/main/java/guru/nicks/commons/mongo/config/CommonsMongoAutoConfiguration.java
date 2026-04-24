package guru.nicks.commons.mongo.config;

import guru.nicks.commons.mongo.MongoCascadeSaveListener;
import guru.nicks.commons.mongo.audit.AuditDetailsDocument;
import guru.nicks.commons.mongo.audit.MongoAuditor;
import guru.nicks.commons.mongo.domain.MongoConstants;
import guru.nicks.commons.mongo.domain.MyMongoProperties;
import guru.nicks.commons.mongo.mapper.MongoAuditDetailsMapper;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClients;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.BsonDateTime;
import org.bson.UuidRepresentation;
import org.bson.types.Decimal128;
import org.mapstruct.factory.Mappers;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.env.Environment;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.event.ValidatingEntityCallback;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Transaction managers and transaction templates aren't created automatically. This is intentional: some projects have
 * multi-tenancy, some do not (see {@link MongoConstants} for bean names). Some projects may combine JPA transactions
 * with Mongo ones (which requires one of the beans to be primary), some may not.
 */
@AutoConfiguration
@EnableConfigurationProperties(MyMongoProperties.class)
@EnableMongoAuditing(auditorAwareRef = "mongoAuditor", dateTimeProviderRef = "mongoAuditDateTimeProvider")
@EnableTransactionManagement
@Slf4j
public class CommonsMongoAutoConfiguration {

    /**
     * {@code scheme://user:password@host1,host2/database?options}
     */
    private static final String DB_URI_FORMAT = "%s://%s:%s@%s/%s%s";

    /**
     * Tells MongoDB how to store UUIDs - as {@link UuidRepresentation#STANDARD}. This is not a conditional bean.
     */
    @Bean
    public MongoClientSettingsBuilderCustomizer commonsMongoClientSettingsBuilderCustomizer() {
        log.debug("Building {} bean for UUID representation",
                MongoClientSettingsBuilderCustomizer.class.getSimpleName());
        return builder -> builder.uuidRepresentation(UuidRepresentation.STANDARD);
    }

    @ConditionalOnMissingBean(name = "mongoAuditor")
    @Bean("mongoAuditor")
    public AuditorAware<AuditDetailsDocument> mongoAuditor() {
        log.debug("Building {} bean", MongoAuditor.class.getSimpleName());
        return new MongoAuditor();
    }

    @ConditionalOnMissingBean
    @Bean
    public MongoAuditDetailsMapper mongoAuditDetailsMapper() {
        log.debug("Building {} bean", MongoAuditDetailsMapper.class.getSimpleName());
        return Mappers.getMapper(MongoAuditDetailsMapper.class);
    }

    /**
     * Creates database factory based on {@link MyMongoProperties}. This factory is the only part of Mongo framework
     * responsible for multi-tenancy. All other components depend on it and thus multi-tenancy works seamlessly.
     *
     * @param properties  properties for Mongo connection
     * @param customizers MongoDB client settings customizers
     * @return database factory
     */
    @ConditionalOnMissingBean
    @Bean
    public MongoDatabaseFactory mongoDatabaseFactory(MyMongoProperties properties, Environment environment,
            List<MongoClientSettingsBuilderCustomizer> customizers) {
        log.debug("Building {} bean", MongoDatabaseFactory.class.getSimpleName());

        var connectionString = deriveConnectionString(properties, environment);
        var settingsBuilder = MongoClientSettings
                .builder()
                .applyConnectionString(new ConnectionString(connectionString));

        // apply all customizers (e.g., UUID representation set in this class) manually - Spring doesn't do it
        // automatically if custom DB factory is defined
        for (var customizer : customizers) {
            customizer.customize(settingsBuilder);
        }

        var mongoClient = MongoClients.create(settingsBuilder.build());
        return new SimpleMongoClientDatabaseFactory(mongoClient, properties.getDatabase());
    }

    private String deriveConnectionString(MyMongoProperties mongoProperties, Environment environment) {
        String explicitUri = environment.getProperty("spring.data.mongodb.uri");
        // during testing, URI points to TestContainers and contains no auth, so DB_URI_FORMAT is not applicable
        if (StringUtils.isNotBlank(explicitUri)) {
            return explicitUri;
        }

        return String.format(Locale.US, DB_URI_FORMAT, mongoProperties.getScheme(),
                mongoProperties.getUsername(), mongoProperties.getPassword(),
                mongoProperties.getSeedList(), mongoProperties.getDatabase(), mongoProperties.getOptions());
    }

    /**
     * Adds support for {@link NotNull @NonNull} and other constraints at the document level.
     */
    @Bean
    public ValidatingEntityCallback commonsValidatingMongoEventListener(LocalValidatorFactoryBean validator) {
        log.debug("Building {} bean for constraint-based validation of Mongo entities",
                ValidatingEntityCallback.class.getSimpleName());
        return new ValidatingEntityCallback(validator);
    }

    @Bean
    public MongoCascadeSaveListener mongoCascadeSaveListener(MongoTemplate mongoTemplate) {
        log.debug("Building {} bean", MongoCascadeSaveListener.class.getSimpleName());
        return new MongoCascadeSaveListener(mongoTemplate);
    }

    /**
     * Reads/writes {@link ZonedDateTime}/{@link LocalDate} (as {@link Date} in Mongo) and {@link BigDecimal} (as
     * {@link Decimal128} in Mongo).
     *
     * @return bean
     */
    @Bean
    public MongoCustomConversions commonsMongoCustomConversions() {
        log.debug("Building {} bean for date and decimal conversions",
                MongoCustomConversions.class.getSimpleName());
        return new MongoCustomConversions(List.of(
                new LocalDateToMongoDateConverter(), new MongoDateToLocalDateConverter(),
                new ZonedDateTimeToMongoDateConverter(), new MongoDateToZonedDateTimeConverter(),
                new BigDecimalToDecimal128Converter(), new Decimal128ToBigDecimalConverter()));
    }

    /**
     * Referenced by {@link EnableMongoAuditing#dateTimeProviderRef()}. Needed because natively Mongo auditor only can
     * initialize {@code org.joda.time.DateTime, org.joda.time.LocalDateTime, java.util.Date, java.lang.Long, long}.
     * This approach differs from {@link MongoCustomConversions} which deals with date properties not related to audit.
     *
     * @return bean
     */
    @ConditionalOnMissingBean(name = "mongoAuditDateTimeProvider")
    @Bean("mongoAuditDateTimeProvider")
    public DateTimeProvider mongoAuditDateTimeProvider() {
        log.debug("Building {} bean for Mongo audit date/time provider", DateTimeProvider.class.getSimpleName());
        return () -> Optional.of(Instant.now());
    }

    /**
     * Stores document's {@link ZonedDateTime} as Mongo's native {@link Date}.
     */
    public static class ZonedDateTimeToMongoDateConverter implements Converter<ZonedDateTime, Date> {

        @Override
        public Date convert(ZonedDateTime source) {
            return Date.from(source.toInstant());
        }
    }

    /**
     * Assigns document's {@link ZonedDateTime} fields from Mongo's native {@link Date}.
     *
     * @see LocalDateToMongoDateConverter
     */
    public static class MongoDateToZonedDateTimeConverter implements Converter<Date, ZonedDateTime> {

        @Override
        public ZonedDateTime convert(Date source) {
            return ZonedDateTime.ofInstant(source.toInstant(), ZoneOffset.UTC);
        }

    }

    /**
     * Mongo always stores dates as {@link BsonDateTime}, i.e. the time part is always there. To write it reliably
     * across different client/server time zones, midnight is always enforced - in UTC timezone. Without this fix, dates
     * may be shifted by 1 day during reading.
     */
    public static class LocalDateToMongoDateConverter implements Converter<LocalDate, Date> {

        @SuppressWarnings("java:S2143") // allow pre-Java8 dates
        @Override
        public Date convert(LocalDate source) {
            return new Date(source.atStartOfDay()
                    .atZone(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli());
        }

    }

    /**
     * Assigns document's {@link LocalDate} fields from Mongo's native {@link Date} ones.
     *
     * @see LocalDateToMongoDateConverter
     */
    public static class MongoDateToLocalDateConverter implements Converter<Date, LocalDate> {

        @Override
        public LocalDate convert(Date source) {
            return source.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        }

    }

    /**
     * Stores document's {@link BigDecimal} as Mongo's native {@link Decimal128}.
     */
    public static class BigDecimalToDecimal128Converter implements Converter<BigDecimal, Decimal128> {

        @Override
        public Decimal128 convert(BigDecimal source) {
            return new Decimal128(source);
        }

    }

    /**
     * Assigns document's {@link BigDecimal} fields from Mongo's native {@link Decimal128} ones.
     *
     * @see BigDecimalToDecimal128Converter
     */
    public static class Decimal128ToBigDecimalConverter implements Converter<Decimal128, BigDecimal> {

        @Override
        public BigDecimal convert(Decimal128 source) {
            return source.bigDecimalValue();
        }

    }

}
