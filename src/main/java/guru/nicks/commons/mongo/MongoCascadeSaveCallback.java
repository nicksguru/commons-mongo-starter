package guru.nicks.commons.mongo;

import guru.nicks.commons.cache.domain.CacheConstants;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.annotation.Id;
import org.springframework.data.mapping.MappingException;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.DocumentReference;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.data.mongodb.core.aggregation.Fields.UNDERSCORE_ID;

/**
 * Called before all writes, not only for fields annotated with {@link MongoCascadeSave MongoCascadeSave}.
 */
@RequiredArgsConstructor
@Slf4j
public class MongoCascadeSaveCallback implements ReflectionUtils.FieldCallback {

    /**
     * Used by {@link #ensureClassHasPrimaryKeyField(Class)}. It's important to keep this field {@code static} because a
     * new callback instance is created for each save event.
     *
     * @see MongoCascadeSaveListener
     */
    private static final Cache<Class<?>, Boolean> CLASS_HAS_PRIMARY_FIELD_CACHE = Caffeine.newBuilder()
            .maximumSize(CacheConstants.DEFAULT_CAFFEINE_CACHE_CAPACITY)
            .build();

    private final Object parentDocument;
    private final MongoTemplate mongoTemplate;

    @Override
    public void doWith(Field field) throws IllegalAccessException {
        // skip low-level things like BSONObject
        try {
            ReflectionUtils.makeAccessible(field);
        } catch (InaccessibleObjectException e) {
            log.warn("Failed to make field '{}' accessible - cascade save not applicable", field);
            return;
        }

        boolean hasRef = field.isAnnotationPresent(DBRef.class) || field.isAnnotationPresent(DocumentReference.class);
        boolean hasCascadeSave = field.isAnnotationPresent(MongoCascadeSave.class);
        boolean eligibleForCascadeSave = hasRef && hasCascadeSave;

        if (!eligibleForCascadeSave) {
            if (hasCascadeSave) {
                throw new IllegalStateException("@CascadeSave without @DBRef/@DocumentReference");
            }

            // just a hint which may or may not be an issue (it's OK to refer to objects already existing in DB, without
            // doing any cascading operations on them)
            if (hasRef && log.isTraceEnabled()) {
                log.warn("Field '{}' has @DBRef/@DocumentReference but has no @CascadeSave", field.getName());
            }

            return;
        }

        Object childDocument = field.get(parentDocument);
        if (childDocument == null) {
            return;
        }

        // see problem explained in @CascadeSave documentation
        if (Arrays.stream(field.getAnnotationsByType(DocumentReference.class))
                .anyMatch(DocumentReference::lazy)) {
            throw new MappingException("@DocumentReference must have lazy=false for "
                    + parentDocument.getClass().getName() + "." + field.getName()
                    + ", otherwise cascade save fails");
        }

        if (childDocument instanceof Iterable<?> children) {
            performCascadeSave(field.getName(), children);
            return;
        }

        performCascadeSave(field.getName(), List.of(childDocument));
    }

    /**
     * Saves cascade children belonging to one parent field. Documents already having an ID are persisted with a single
     * bulk operation per document class, unlike one {@link MongoTemplate#save(Object)} network round trip per
     * document. Documents having no ID are still saved one by one because only this way Spring Data writes the
     * generated ID back into the source object, and the parent's {@link DBRef @DBRef}/
     * {@link DocumentReference @DocumentReference} refers to that ID. Lifecycle events (such as nested cascade saves)
     * and before-convert callbacks (such as auditing) are preserved because bulk operations emit them too.
     *
     * @param fieldName parent's field holding the documents
     * @param documents documents to save
     */
    private void performCascadeSave(String fieldName, Iterable<?> documents) {
        // one bulk operation per document class because a bulk operation targets a single collection
        Map<Class<?>, List<Object>> documentsByClass = new LinkedHashMap<>();
        documents.forEach(document ->
                documentsByClass.computeIfAbsent(document.getClass(), key -> new ArrayList<>()).add(document));

        documentsByClass.forEach((documentClass, classDocuments) -> {
            ensureClassHasPrimaryKeyField(documentClass);
            log.debug("Performing cascade save of {} document(s) of '{}' from {}", classDocuments.size(), fieldName,
                    parentDocument.getClass().getName());

            BulkOperations bulkOperations = null;

            for (Object document : classDocuments) {
                // the ID must be taken from the converted document because its BSON representation (such as String
                // vs ObjectId) is what's actually stored
                Document convertedDocument = new Document();
                mongoTemplate.getConverter().write(document, convertedDocument);
                Object id = convertedDocument.get(UNDERSCORE_ID);

                if (id == null) {
                    mongoTemplate.save(document);
                    continue;
                }

                if (bulkOperations == null) {
                    bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.ORDERED,
                            ClassUtils.getUserClass(documentClass));
                }

                // 'save' semantics: replace the whole document or insert it if it doesn't exist yet
                bulkOperations.replaceOne(new Query(Criteria.where(UNDERSCORE_ID).is(id)), document,
                        new FindAndReplaceOptions().upsert());
            }

            if (bulkOperations != null) {
                bulkOperations.execute();
            }
        });
    }

    /**
     * Checks if the argument has a field annotated with {@link Id @Id}. Leverages caching because this kind of
     * knowledge is constant.
     *
     * @param clazz class to check
     * @throws MappingException field not found
     */
    private void ensureClassHasPrimaryKeyField(Class<?> clazz) {
        // 'get' method may return null as per Caffeine specs, but never does in this particular case
        //noinspection DataFlowIssue
        boolean hasPrimaryKeyField = CLASS_HAS_PRIMARY_FIELD_CACHE.get(clazz, classToCheck -> {
            var callback = new PrimaryKeyFieldCallback();
            ReflectionUtils.doWithFields(classToCheck, callback);

            log.debug("Uncached search (should happen only once per class): class {} has @Id: {}",
                    classToCheck.getName(), callback.isHasPrimaryKeyField());
            return callback.isHasPrimaryKeyField();
        });

        if (!hasPrimaryKeyField) {
            throw new MappingException("Cannot perform cascade save on object having no @Id field");
        }
    }

    /**
     * If a field annotated with {@link Id @Id} is found, {@link #isHasPrimaryKeyField()} returns true.
     */
    private static class PrimaryKeyFieldCallback implements ReflectionUtils.FieldCallback {

        @Getter
        private boolean hasPrimaryKeyField;

        public void doWith(Field field) {
            ReflectionUtils.makeAccessible(field);
            hasPrimaryKeyField |= field.isAnnotationPresent(Id.class);
        }

    }

}
