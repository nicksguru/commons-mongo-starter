package guru.nicks.commons.mongo.mapper;

import guru.nicks.commons.mongo.domain.FullTextSearchAwareDocument;

import org.mapstruct.Mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * For MapStruct mappers: ignores full-text search fields, see {@link FullTextSearchAwareDocument}. They are maintained
 * by the application on save, never copied from DTOs.
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.CLASS)
@Mapping(target = "fullTextSearchDataHighPriority", ignore = true)
@Mapping(target = "fullTextSearchDataLowPriority", ignore = true)
@Mapping(target = "documentLanguage", ignore = true)
public @interface IgnoreMongoFullTextSearchFields {
}
