package guru.nicks.commons.mongo;

import guru.nicks.commons.cache.domain.CacheConstants;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.Id;
import org.springframework.data.mapping.MappingException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.DocumentReference;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.util.Arrays;

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

        if (childDocument instanceof Iterable) {
            ((Iterable<?>) childDocument).forEach(document ->
                    performCascadeSave(field.getName(), document));
            return;
        }

        performCascadeSave(field.getName(), childDocument);
    }

    private void performCascadeSave(String fieldName, Object document) {
        ensureClassHasPrimaryKeyField(document.getClass());
        log.debug("Performing cascade save of '{} {}' from {}", ClassUtils.getUserClass(document.getClass()).getName(),
                fieldName, parentDocument.getClass().getName());
        mongoTemplate.save(document);
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
