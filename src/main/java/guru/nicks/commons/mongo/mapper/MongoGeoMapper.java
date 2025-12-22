package guru.nicks.commons.mongo.mapper;

import guru.nicks.commons.mapper.DefaultMapStructConfig;
import guru.nicks.commons.rest.v1.dto.GeoPointDto;

import org.mapstruct.Mapper;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

@Mapper(config = DefaultMapStructConfig.class)
public class MongoGeoMapper {

    public GeoPointDto toDto(Point source) {
        if (source == null) {
            return null;
        }

        return new GeoPointDto(source.getY(), source.getX());
    }

    public GeoJsonPoint toPoint(GeoPointDto dto) {
        if (dto == null) {
            return null;
        }

        return new GeoJsonPoint(dto.lon(), dto.lat());
    }

}
