package guru.nicks.commons.mongo.impl;

import guru.nicks.commons.mongo.domain.MongoConstants;
import guru.nicks.commons.mongo.domain.SequenceDocument;
import guru.nicks.commons.mongo.service.MongoSequenceService;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import static org.springframework.data.mongodb.core.aggregation.Fields.UNDERSCORE_ID;

@Service
@RequiredArgsConstructor
public class MongoSequenceServiceImpl implements MongoSequenceService {

    /**
     * All DB sequences MUST reside in a shared DB excluded from multi-tenancy. Otherwise, their values will collide.
     */
    @Qualifier(MongoConstants.SHARED_MONGO_TEMPLATE_BEAN)
    private final MongoTemplate mongoTemplate;

    @Override
    public long generateNextValue(String sequenceName) {
        SequenceDocument doc = mongoTemplate.findAndModify(
                new Query(Criteria.where(UNDERSCORE_ID).is(sequenceName)),
                new Update().inc(SequenceDocument.Fields.value, 1),
                // WARNING: as per https://www.mongodb.com/docs/manual/reference/method/db.collection.findAndModify/,
                // to avoid upsert duplicates, ensure that the query field (_id in this case) is uniquely indexed!
                new FindAndModifyOptions().returnNew(true).upsert(true),
                SequenceDocument.class);

        // with upsert+new, doc is never null, so this is just for extra safety
        return (doc == null)
                ? 1
                : doc.getValue();
    }

}
