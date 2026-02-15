package guru.nicks.commons.mongo.mapper;

import guru.nicks.commons.mapper.DefaultMapStructConfig;
import guru.nicks.commons.mongo.audit.AuditableDocument;
import guru.nicks.commons.rest.v1.dto.AuditDetailsDto;

import org.mapstruct.Mapper;

@Mapper(config = DefaultMapStructConfig.class)
public interface MongoAuditDetailsMapper {

    AuditDetailsDto toDto(AuditableDocument<?> source);

}
