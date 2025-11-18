package guru.nicks.commons.mongo.impl;

import guru.nicks.commons.cloud.domain.CloudFile;
import guru.nicks.commons.exception.http.NotFoundException;
import guru.nicks.commons.service.CloudFileStorageService;
import guru.nicks.commons.utils.ResourceUtils;

import am.ik.yavi.meta.ConstraintArguments;
import com.mongodb.BasicDBObject;
import com.mongodb.client.gridfs.model.GridFSFile;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.BsonObjectId;
import org.bson.BsonValue;
import org.bson.types.ObjectId;
import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsCriteria;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.MediaType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static guru.nicks.commons.validation.dsl.ValiDsl.checkNotBlank;
import static guru.nicks.commons.validation.dsl.ValiDsl.checkNotNull;
import static org.springframework.data.mongodb.core.aggregation.Fields.UNDERSCORE_ID;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Mongo GridFS-based implementation.
 */
@RequiredArgsConstructor
public class MongoCloudFileStorageServiceImpl implements CloudFileStorageService {

    @NonNull // Lombok creates runtime nullness check for this own annotation only
    private final GridFsTemplate gridFsTemplate;

    /**
     * NOTE: stream must be <b>read fully</b>, in order to calculate its checksum, however no more than
     * {@link Integer#MAX_VALUE} bytes can be read here.
     *
     * @throws IllegalArgumentException stream size is larger than {@link Integer#MAX_VALUE}
     */
    @ConstraintArguments
    @Override
    public CloudFile save(@Nullable String userId, InputStream inputStream, String filename, MediaType contentType,
            Map<String, ?> metadata) {
        checkNotNull(inputStream, _MongoCloudFileStorageServiceImplSaveArgumentsMeta.INPUTSTREAM.name());
        checkNotBlank(filename, _MongoCloudFileStorageServiceImplSaveArgumentsMeta.FILENAME.name());
        checkNotNull(contentType, _MongoCloudFileStorageServiceImplSaveArgumentsMeta.CONTENTTYPE.name());

        // collect IDs of already existing files with the same filename
        var oldFileIds = new HashSet<String>();
        gridFsTemplate.find(query(GridFsCriteria.whereFilename().is(filename)))
                .map(GridFSFile::getId)
                .map(BsonValue::asObjectId)
                .map(BsonObjectId::getValue)
                .map(ObjectId::toString)
                .forEach(oldFileIds::add);

        var dbMetadata = new BasicDBObject();

        if (MapUtils.isNotEmpty(metadata)) {
            dbMetadata.putAll(metadata);
        }

        if (StringUtils.isNotBlank(userId)) {
            dbMetadata.put("userId", userId);
        }

        // calculate content checksum (can't read more than Integer.MAX_VALUE bytes!)
        byte[] content;
        try {
            content = IOUtils.toByteArray(inputStream);
        } catch (IOException e) {
            throw new IllegalArgumentException("Stream failure or stream too large: " + e.getMessage(), e);
        }

        dbMetadata.put("checksum", computeChecksum(content));
        ObjectId id = gridFsTemplate.store(new ByteArrayInputStream(content), filename, contentType.toString(),
                dbMetadata);
        // delete previous file versions
        oldFileIds.forEach(this::deleteById);

        return getById(id.toString());
    }

    @Override
    public Optional<CloudFile> findByFilename(String filename) {
        GridFsResource resource = gridFsTemplate.getResource(filename);

        return Optional.of(resource)
                .filter(Resource::exists)
                .map(this::retrieveResourceInfo);
    }

    @Override
    public Optional<CloudFile> findById(String id) {
        GridFSFile file = gridFsTemplate.findOne(new Query(
                Criteria.where(UNDERSCORE_ID).is(id)));

        return Optional.ofNullable(file)
                .map(gridFsTemplate::getResource)
                .filter(Resource::exists)
                .map(this::retrieveResourceInfo);
    }

    @Override
    public InputStream getInputStream(String id) {
        GridFSFile file = gridFsTemplate.findOne(new Query(
                Criteria.where(UNDERSCORE_ID).is(id)));

        return Optional.ofNullable(file)
                .map(gridFsTemplate::getResource)
                .filter(Resource::exists)
                .map(resource -> {
                    try {
                        return resource.getInputStream();
                    } catch (IOException e) {
                        throw new NotFoundException(e.getMessage(), e);
                    }
                })
                .orElseThrow(NotFoundException::new);
    }

    @Override
    public List<CloudFile> listFiles(String path) {
        String regex = ResourceUtils.normalizePathPrefix(path) + "*";
        var query = new Query(GridFsCriteria.whereFilename().regex(regex));
        var files = new ArrayList<CloudFile>();

        gridFsTemplate.find(query)
                .map(gridFsTemplate::getResource)
                .map(this::retrieveResourceInfo)
                .into(files);
        return files;
    }

    @Override
    public void deleteById(String id) {
        gridFsTemplate.delete(new Query(
                Criteria.where(UNDERSCORE_ID).is(id)));
    }

    @SneakyThrows
    private CloudFile retrieveResourceInfo(GridFsResource resource) {
        return CloudFile.builder()
                .id(((BsonObjectId) resource.getId()).getValue().toHexString())
                .filename(resource.getFilename())
                .userId(findUserIdInMetadata(resource).orElse(null))
                .checksum(findChecksumInMetadata(resource).orElse(null))
                .contentType(MediaType.valueOf(resource.getContentType()))
                .size(resource.contentLength())
                .lastModified(Instant.ofEpochMilli(resource.lastModified()))
                .build();
    }

    private Optional<String> findUserIdInMetadata(GridFsResource resource) {
        // metadata, at least empty, always exists
        return Optional.ofNullable(resource.getOptions().getMetadata().get("userId"))
                .map(Object::toString);
    }

    private Optional<String> findChecksumInMetadata(GridFsResource resource) {
        // metadata, at least empty, always exists
        return Optional.ofNullable(resource.getOptions().getMetadata().get("checksum"))
                .map(Object::toString);
    }

}
