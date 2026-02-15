package guru.nicks.commons.mongo.domain;

import lombok.experimental.UtilityClass;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Collation;

import java.util.Locale;

@UtilityClass
public class MongoConstants {

    /**
     * Search with collation strength 1 (which this field defines) coalesces diacritics, which means 'â' becomes the
     * same as 'a'. Collation strength 2 would only ignore the letter case, which is not enough to make search
     * human-oriented (both typo- and case-tolerant).
     * <p>
     * WARNING: this collation must be specified for each search explicitly, otherwise default binary collation will be
     * applied. Another approach is to make {@link #COLLATION_IGNORING_CASE_AND_DIACRITICS_JSON} the default, but it
     * imposes the risk or mismatching technical strings whose case matters.
     */
    public static final Collation COLLATION_IGNORING_CASE_AND_DIACRITICS = Collation
            .of(Locale.FRENCH)
            .strength(Collation.ComparisonLevel.primary());

    /**
     * Same as {@link #COLLATION_IGNORING_CASE_AND_DIACRITICS}, just as a JSON string. To be used during collection
     * creation as {@link Document#collation()} to make <b>all</b> string indexes and search queries use this collation
     * by default. This can be seen as MySQL VARCHAR behavior (which is case-insensitive) augmented with ignoring
     * diacritics.
     * <p>
     * WARNING: this collation also affects such string fields as URLs and foreign keys. This can cause mismatches. If
     * there's at least one field that depends on character case, do NOT override collection's default collation.
     */
    public static final String COLLATION_IGNORING_CASE_AND_DIACRITICS_JSON = "{\"locale\": \"fr\", \"strength\": 1}";

    /**
     * Recommended number of languages to try during full text search. This does not include the extra attempt of
     * falling back on {@link #FALLBACK_SEARCH_LANGUAGE}, this is to try user-provided languages.
     */
    public static final int MAX_SEARCH_LANGUAGES_TO_TRY = 2;
    /**
     * Recommended fallback languages for full text search.
     *
     * @see #MAX_SEARCH_LANGUAGES_TO_TRY
     */
    public static final MongoSearchLanguage FALLBACK_SEARCH_LANGUAGE = MongoSearchLanguage.EN;

    /**
     * For another (or perhaps the same) database holding NON-multi-tenant data. Usage:
     * {@code @Qualifier(MongoConstants.SHARED_MONGO_TEMPLATE)}.
     */
    public static final String SHARED_MONGO_TEMPLATE_BEAN = "sharedMongoTemplate";
    /**
     * For another (or perhaps the same) database holding NON-multi-tenant data. Usage:
     * {@code @Transactional(transactionalManager = MongoConstants.SHARED_TRANSACTION_MANAGER_BEAN)}. Don't rely on bare
     * {@code @Transactional}!
     */
    public static final String SHARED_TRANSACTION_MANAGER_BEAN = "sharedMongoTransactionManager";
    public static final String SHARED_TRANSACTION_TEMPLATE_BEAN = "sharedMongoTransactionTemplate";
    public static final String SHARED_DATABASE_FACTORY_BEAN = "sharedMongoDatabaseFactory";

}
