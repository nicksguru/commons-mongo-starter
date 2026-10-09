package guru.nicks.commons.mongo.mapper;

import guru.nicks.commons.mongo.audit.AuditableDocument;

import org.mapstruct.Mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * For MapStruct mappers: ignores audit fields, see {@link AuditableDocument}. They are filled by Spring Data JPA
 * auditing, never copied from DTOs.
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.CLASS)
@Mapping(target = "createdDate", ignore = true)
@Mapping(target = "createdBy", ignore = true)
@Mapping(target = "lastModifiedDate", ignore = true)
@Mapping(target = "lastModifiedBy", ignore = true)
public @interface IgnoreMongoAuditFields {
}
