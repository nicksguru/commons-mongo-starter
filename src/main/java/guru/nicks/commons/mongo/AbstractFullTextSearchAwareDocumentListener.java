package guru.nicks.commons.mongo;

import guru.nicks.commons.mongo.domain.FullTextSearchAwareDocument;
import guru.nicks.commons.utils.text.NgramUtils;
import guru.nicks.commons.utils.text.NgramUtilsConfig;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertEvent;
import org.springframework.util.unit.DataSize;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Before {@link FullTextSearchAwareDocument} is saved, sets
 * {@link FullTextSearchAwareDocument#getFullTextSearchDataHighPriority()} and
 * {@link FullTextSearchAwareDocument#getFullTextSearchDataLowPriority()} based on ngrams derived from
 * {@link FullTextSearchAwareDocument#getFullTextSearchDataSuppliers()} values. The goal is to use those fields for
 * fuzzy full text search.
 * <p>
 * The length of each of the above two fields is limited to {@link #MAX_FULL_TEXT_SEARCH_DATA_LENGTH}.
 * <p>
 * WARNING: this approach only works when using Spring Data to save documents. It's based on Mongo Spring events.
 */
@Slf4j
public abstract class AbstractFullTextSearchAwareDocumentListener
        extends AbstractMongoEventListener<FullTextSearchAwareDocument<?>> {

    /**
     * Limit to 1Mb to avoid DB oversizing (although Mongo permits 16Mb per document).
     */
    public static final int MAX_FULL_TEXT_SEARCH_DATA_LENGTH = Math.toIntExact(DataSize.ofMegabytes(1).toBytes());

    @Override
    public void onBeforeConvert(BeforeConvertEvent<FullTextSearchAwareDocument<?>> event) {
        FullTextSearchAwareDocument<?> source = event.getSource();

        String str = source.getFullTextSearchDataSuppliers()
                .stream()
                .filter(Objects::nonNull)
                .map(Supplier::get)
                .filter(Objects::nonNull)
                .map(Object::toString)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(" "));

        String ngrams = String.join(" ",
                NgramUtils.createNgrams(str, NgramUtils.Mode.PREFIX_ONLY, getNgramUtilsConfig()));
        source.setFullTextSearchDataHighPriority(StringUtils.substring(ngrams, 0, MAX_FULL_TEXT_SEARCH_DATA_LENGTH));

        ngrams = String.join(" ",
                NgramUtils.createNgrams(str, NgramUtils.Mode.INFIX_ONLY, getNgramUtilsConfig()));
        source.setFullTextSearchDataLowPriority(StringUtils.substring(ngrams, 0, MAX_FULL_TEXT_SEARCH_DATA_LENGTH));

        if (log.isTraceEnabled()) {
            log.trace("Set fullSearchDataHighPriority={}, fullSearchDataLowPriority={} for [{}] ID '{}'",
                    source.getFullTextSearchDataHighPriority(), source.getFullTextSearchDataLowPriority(),
                    source.getClass().getName(), source.getId());
        } else {
            log.debug("Rebuilt full-text search ngrams for [{}] ID '{}'", source.getClass().getName(), source.getId());
        }
    }

    protected abstract NgramUtilsConfig getNgramUtilsConfig();

}
