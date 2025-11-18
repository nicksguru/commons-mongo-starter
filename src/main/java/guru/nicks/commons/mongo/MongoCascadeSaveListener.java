package guru.nicks.commons.mongo;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

/**
 * If the document being saved has properties (nested objects) annotated with
 * {@link MongoCascadeSave @MongoCascadeSave}, performs transparent cascade save (but never removal) of such
 * properties.
 * <p>
 * WARNING: this approach only works when using Spring Data to save documents. It's based on Mongo Spring events.
 */
@Component
@RequiredArgsConstructor
public class MongoCascadeSaveListener extends AbstractMongoEventListener<Object> {

    // DI
    private final MongoTemplate mongoTemplate;

    @Override
    public void onBeforeConvert(BeforeConvertEvent<Object> event) {
        Object document = event.getSource();
        ReflectionUtils.doWithFields(document.getClass(), new MongoCascadeSaveCallback(document, mongoTemplate));
    }

}
