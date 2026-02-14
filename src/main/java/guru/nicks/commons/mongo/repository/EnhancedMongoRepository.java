package guru.nicks.commons.mongo.repository;

import guru.nicks.commons.ApplicationContextHolder;
import guru.nicks.commons.mongo.audit.AuditDetailsDocument;
import guru.nicks.commons.mongo.audit.AuditableDocument;
import guru.nicks.commons.mongo.domain.MongoConstants;
import guru.nicks.commons.mongo.domain.MongoSearchLanguage;
import guru.nicks.commons.utils.ReflectionUtils;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import jakarta.annotation.Nullable;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Persistable;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.mapping.BasicMongoPersistentProperty;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.support.SpringDataMongodbQuery;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.data.mongodb.core.aggregation.Fields.UNDERSCORE_ID;

/**
 * A combination of common Mongo-related repository interfaces. MongoDB has ACID transaction support, it's leveraged by
 * Spring Data with {@code @Transactional(MongoConstants.TRANSACTION_MANAGER_BEAN)} provided that
 * {@link MongoTransactionManager} has been configured.
 *
 * @param <T>  document type
 * @param <ID> primary key type
 * @param <E>  exception type to throw when document is not found
 * @param <F>  filter type (pass {@code Void} for no filter)
 * @see #assignAuditData(AuditableDocument)
 */
@NoRepositoryBean
@SuppressWarnings("java:S119")  // allow type names like 'ID'
public interface EnhancedMongoRepository<T extends Persistable<ID>, ID, E extends RuntimeException, F>
        extends MongoRepository<T, ID>, QuerydslPredicateExecutor<T> {

    /**
     * Recommended value, for example, for {@link AggregationOptions.Builder#cursorBatchSize(int)} - it should always be
     * passed to {@link Aggregation#withOptions(AggregationOptions)} if it's presented as a stream
     * ({@link MongoTemplate#aggregateStream(Aggregation, String, Class)}) to avoid OOM.
     */
    int DB_BATCH_SIZE = 500;

    /**
     * @see #getDocumentClass()
     */
    Class<?> STATIC_THIS = MethodHandles.lookup().lookupClass();

    /**
     * Does what Spring Data listener does (but if ID is set manually, Spring doesn't set createdDate; this method
     * does). Needed for low-level operations that bypass the listener, such as {@link BulkOperations}, and when the
     * primary key is assigned manually. Assigns:
     * <ol>
     *     <li>{@link AuditableDocument#getLastModifiedDate()} - always</li>
     *     <li>{@link AuditableDocument#getCreatedDate()} - if it's {@code null}</li>
     *     <li>{@link AuditableDocument#getLastModifiedBy()} - if current user is known</li>
     *     <li>{@link AuditableDocument#getCreatedBy()} - if it's {@code null} and current user is known</li>
     * </ol>
     *
     * @param doc auditable document
     */
    static void assignAuditData(AuditableDocument doc) {
        // assign modification date and initialize creation date (Spring assigns both on insert)
        doc.setLastModifiedDate(Instant.now());

        if (doc.getCreatedDate() == null) {
            doc.setCreatedDate(doc.getLastModifiedDate());
        }

        // assign (but not overwrite) creator, assign modifier - but if current user is unknown, previous values remain
        Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(Authentication::getPrincipal)
                .filter(UserDetails.class::isInstance)
                .map(UserDetails.class::cast)
                .map(AuditDetailsDocument::new)
                .ifPresent(auditDetails -> {
                    doc.setLastModifiedBy(auditDetails);

                    if (doc.getCreatedBy() == null) {
                        doc.setCreatedBy(auditDetails);
                    }
                });
    }

    default Logger getLog() {
        return LoggerFactory.getLogger(AopUtils.getTargetClass(this));
    }

    /**
     * Adds search condition to query if search value is not {@code null}.
     *
     * @param target    QueryDSL builder
     * @param condition search condition to add
     * @param supplier  value supplier (can't pass the value itself because Spring Data wraps this method in its special
     *                  invocation handler which rejects null values)
     * @param <V>       value type
     */
    default <V> void andIfNotNull(Supplier<V> supplier, BooleanBuilder target, Function<V, Predicate> condition) {
        Optional.ofNullable(supplier.get())
                .map(condition)
                .ifPresent(target::and);
    }

    /**
     * Adds search condition to query if search value is a not-blank string.
     *
     * @param target    QueryDSL builder
     * @param condition search condition to add
     * @param supplier  value supplier (can't pass the value itself because Spring Data wraps this method in its special
     *                  invocation handler which rejects null values)
     */
    default void andIfNotBlank(Supplier<String> supplier, BooleanBuilder target,
            Function<String, Predicate> condition) {
        Optional.ofNullable(supplier.get())
                .filter(StringUtils::isNotBlank)
                .map(condition)
                .ifPresent(target::and);
    }

    /**
     * Retrieves {@code T} class out of the concrete interface's {@link Persistable} generic parameter.
     * <p>
     * WARNING: if this interface has an intermediate subinterface (from which repositories inherit directly), that
     * subinterface must override this method.
     *
     * @return {@code T} class
     */
    default Class<T> getDocumentClass() {
        return (Class<T>) ReflectionUtils.findMaterializedGenericType(getClass(), STATIC_THIS, Persistable.class)
                .orElseThrow(() -> new IllegalStateException("Missing mapped class in " + getClass().getName()));
    }

    /**
     * Performs full-text search.
     *
     * @param q              search text
     * @param searchLanguage language to perform search query analysis for
     * @param pageable       pagination criteria
     * @return documents found, most relevant first
     */
    default Page<T> searchFullText(String q, @Nullable MongoSearchLanguage searchLanguage, Pageable pageable) {
        // each document may contain the 'language' field, so language should not matter here, but it does matter
        TextCriteria criteria = (searchLanguage == null)
                ? TextCriteria.forDefaultLanguage()
                : TextCriteria.forLanguage(searchLanguage.name());
        criteria.matching(q);

        // collation doesn't work for full text search
        Query query = TextQuery.queryText(criteria)
                .sortByScore()
                .with(pageable);
        List<T> results = find(query, getDocumentClass());

        // this is what Spring Data does to apply pagination to raw queries
        return PageableExecutionUtils.getPage(results, pageable, () ->
                getMongoTemplate().count(Query.of(query).limit(-1).skip(-1), getDocumentClass()));
    }

    /**
     * Finds documents by filter.
     *
     * @param filter   filter
     * @param pageable pagination
     * @return page of documents found
     */
    default Page<T> findByFilter(@Nullable F filter, Pageable pageable) {
        return convertToSearchPredicate(filter).map(pair -> {
            Predicate predicate = pair.getLeft();
            Collation collation = pair.getRight();
            Class<T> mappedClass = getDocumentClass();
            getLog().info("Finding [{}] with filter {} / collation {} / pagination {}", mappedClass.getName(), filter,
                    collation, pageable);

            Document doc = createQueryDocument(predicate, mappedClass);
            var query = new BasicQuery(doc).with(pageable);
            // add collation to raw query - Spring Data can't handle it on high level
            query.collation(collation);
            // TODO: there seems no other way to grab a bean from an interface. Custom repository implementation won't
            // work here because the actual mapped class is a generic parameter.
            List<T> results = getMongoTemplate().find(query, mappedClass);

            // this is what Spring Data does to apply pagination to raw queries
            return PageableExecutionUtils.getPage(results, pageable,
                    () -> getMongoTemplate().count(Query.of(query).limit(-1).skip(-1), mappedClass));
        }).orElseGet(() -> {
            getLog().info("Finding [{}] with pagination {}", getDocumentClass().getName(), pageable);
            return findAll(pageable);
        });
    }

    /**
     * Translates search filter request to QueryDSL predicate. Better don't use regexps because they don't use indexes
     * and are not collation-aware. It's interesting that Spring Data tries to generate some code for this method and
     * fails with 'No property 'convertToSearchPredicate' found for type 'SomeDocument'.
     *
     * @param filter search filter
     * @return QueryDSL predicate (never {@code null}) and collation to use for search, for example
     *         {@link Collation#simple()} to compare strings byte by byte or
     *         {@link MongoConstants#COLLATION_IGNORING_CASE_AND_DIACRITICS}. Using a non-default collation requires
     *         <b>indexes to be created with the same locale and strength</b>, otherwise default collation will
     *         be applied.
     */
    Optional<Pair<Predicate, Collation>> convertToSearchPredicate(@Nullable F filter);

    /**
     * @return class of {@code E}
     */
    @SuppressWarnings("unchecked")
    default Class<E> getExceptionClass() {
        return (Class<E>) ReflectionUtils
                .findMaterializedGenericType(getClass(), STATIC_THIS, Throwable.class)
                .orElseThrow(() -> new NoSuchElementException("Failed to find generic exception type"));
    }

    /**
     * Unlike {@link #findAllById(Iterable)}, returns elements in the same order as their IDs are listed in arguments.
     *
     * @param ids IDs
     * @return elements in the same order as in {@code ids}, mutable list
     */
    default List<T> findAllByIdPreserveOrder(Iterable<ID> ids) {
        // need indexOf() which only List has
        List<ID> list = (ids instanceof List) ? (List<ID>) ids : IterableUtils.toList(ids);

        return findAllById(list).stream()
                .sorted(Comparator.comparing(document -> list.indexOf(document.getId())))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Subinterfaces may override this to throw more specific exceptions.
     *
     * @param id document ID
     * @return document
     * @throws E document not found
     */
    default T getById(ID id) {
        return findById(id).orElseThrow(() ->
                ReflectionUtils.instantiateEvenWithoutDefaultConstructor(getExceptionClass()));
    }

    /**
     * A more readable shortcut to {@link #findAllBy()}.
     *
     * @return stream of documents
     */
    default Stream<T> findAllAsStream() {
        return findAllBy();
    }

    /**
     * Fetch all documents using DB cursor. Faster than {@link org.springframework.data.domain.Pageable}, requires less
     * memory than {@link #findAll()}. Requires {@code try-with-resources}:
     * <pre>
     *  try (var stream = repository.findAllBy()) {
     *      [...]
     *  }
     * </pre>
     *
     * @return stream of documents
     */
    Stream<T> findAllBy();

    /**
     * In each document returned, the only field set is '_id', its value is maybe {@link org.bson.types.ObjectId}, maybe
     * something else. To not depend on the concrete field type (which is not always the same as in the corresponding
     * Spring Data document definition), consider calling {@link Object#toString()}:
     * <pre>
     *  try (var stream = repository.findAllIds()) {
     *      stream.map(doc -> doc.get(UNDERSCORE_ID))
     *            .map(Object::toString)
     *            .forEach(...);
     *  }
     * </pre>
     *
     * @return projection document
     */
    @org.springframework.data.mongodb.repository.Query(value = "{}", fields = "{_id: 1}")
    Stream<Document> findAllIds();

    /**
     * Returns real property field name, as in the database. May be needed to avoid hardcoded property names in
     * low-level queries (the names may differ because of {@link Field @Field}). Queries constructed using
     * {@link MongoTemplate} ({@link MongoTemplate#updateMulti(Query, UpdateDefinition, Class)},
     * {@link MongoTemplate#indexOps(Class)} and so on) already do this kind of mapping automatically.
     *
     * @param documentClass document class
     * @param propertyName  property name in document class (consider using Lombok {@code Fields} constants)
     * @return field name in DB
     */
    default String getLowLevelFieldName(Class<?> documentClass, String propertyName) {
        return Optional.ofNullable(getMongoMappingContext().getPersistentEntity(documentClass))
                .map(entity -> entity.getPersistentProperty(propertyName))
                .map(BasicMongoPersistentProperty.class::cast)
                .map(BasicMongoPersistentProperty::getFieldName)
                .orElseThrow();
    }

    /**
     * Updates, unlike {@link org.springframework.data.repository.CrudRepository#save(Object)}, certain fields only.
     *
     * @param documentClass  document class
     * @param fieldsToUpdate field names and values (will be passed to {@link #createFullDocumentUpdate(Object)})
     */
    default void updatePartially(Class<?> documentClass, Map<String, Object> fieldsToUpdate) {
        Pair<Query, Update> update = createFullDocumentUpdate(fieldsToUpdate);
        getMongoTemplate().updateFirst(update.getLeft(), update.getRight(), documentClass);
    }

    /**
     * Returns a {@code where} query and a {@code $set}-style update command meant to rewrite all the fields the source
     * object has. This is a shortcut to updating, via {@link MongoConverter}, all the fields without knowing their
     * names, which is needed for non-Spring Data operations, such as {@link BulkOperations}.
     *
     * @param source source object, must be processable {@link MongoConverter} and has either '_id' or 'id' set
     * @return a pair where the first element is the query and the second element is the update command
     */
    default Pair<Query, Update> createFullDocumentUpdate(Object source) {
        var convertedDoc = new Document();
        getMongoTemplate().getConverter().write(source, convertedDoc);
        String id = Objects.toString(convertedDoc.remove(UNDERSCORE_ID), null);

        if (StringUtils.isBlank(id)) {
            id = Objects.toString(convertedDoc.remove("id"), null);

            if (StringUtils.isBlank(id)) {
                throw new IllegalArgumentException("Missing '_id' or 'id' field in the source object");
            }
        }

        // without this, object updated from a Map will have '_class' field set to something like 'java.util.HashMap'
        if (source instanceof Map) {
            convertedDoc.remove("_class");
        }

        // shortcut to setting all fields without knowing their names
        var updateDoc = new Document().append("$set", convertedDoc);

        return Pair.of(
                new Query(Criteria.where(UNDERSCORE_ID).is(id)),
                Update.fromDocument(updateDoc, UNDERSCORE_ID));
    }

    /**
     * Renders search predicate as a query document which can be fed, for example, to
     * {@link BasicQuery#BasicQuery(Document)}.
     *
     * @param predicate   search predicate
     * @param mappedClass class to search for
     * @return query document, never {@code null}
     */
    default Document createQueryDocument(Predicate predicate, Class<T> mappedClass) {
        return new QueryUtils<>(getMongoTemplate(), mappedClass).createQuery(predicate);
    }

    /**
     * Retrieves {@link MongoTemplate} out of {@link ApplicationContextHolder}.
     *
     * @return bean
     * @throws NoSuchBeanDefinitionException no such bean
     */
    default MongoTemplate getMongoTemplate() {
        return ApplicationContextHolder.getApplicationContext()
                .getBean(MongoTemplate.class);
    }

    /**
     * Retrieves {@link MongoMappingContext} out of {@link ApplicationContextHolder}.
     *
     * @return bean
     * @throws NoSuchBeanDefinitionException no such bean
     */
    default MongoMappingContext getMongoMappingContext() {
        return ApplicationContextHolder.getApplicationContext()
                .getBean(MongoMappingContext.class);
    }

    private List<T> find(Query query, Class<T> documentClass) {
        return getMongoTemplate().find(query, documentClass);
    }

    class QueryUtils<T> extends SpringDataMongodbQuery<T> {

        public QueryUtils(MongoOperations operations, Class<? extends T> type) {
            super(operations, type, operations.getCollectionName(type));
        }

        /**
         * Just made this method public as compared to base class. Also, ensured {@code null} was never returned -
         * Spring Data verifies, in non-static repository methods, that all methods return non-{@code null} (an
         * exception is thrown otherwise).
         */
        @Override
        public Document createQuery(@Nullable Predicate predicate) {
            return Optional.ofNullable(super.createQuery(predicate))
                    .orElseGet(Document::new);
        }
    }

}
