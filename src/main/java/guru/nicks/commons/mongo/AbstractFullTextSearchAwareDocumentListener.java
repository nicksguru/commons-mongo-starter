package guru.nicks.commons.mongo;

import guru.nicks.commons.mongo.domain.FullTextSearchAwareDocument;
import guru.nicks.commons.mongo.domain.MongoSearchLanguage;
import guru.nicks.commons.mongo.repository.EnhancedMongoRepository;
import guru.nicks.commons.utils.text.EnglishUtils;
import guru.nicks.commons.utils.text.NgramUtils;
import guru.nicks.commons.utils.text.NgramUtilsConfig;
import guru.nicks.commons.utils.text.TextUtils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertEvent;
import org.springframework.util.unit.DataSize;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.SequencedSet;
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
 * WARNING: this approach only works when using Spring Data to save documents. It's based on Mongo Spring events. Also,
 * this bean is used by {@link EnhancedMongoRepository#searchFullText(String, MongoSearchLanguage, Pageable)}.
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

        String text = source.getFullTextSearchDataSuppliers()
                .stream()
                .filter(Objects::nonNull)
                .map(Supplier::get)
                .filter(Objects::nonNull)
                .map(Object::toString)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(" "));

        // add words that are shorter than the minimum ngram length, otherwise they'll be omitted
        SequencedSet<String> words = TextUtils.collectUniqueWords(text, getNgramUtilsConfig().isReduceAccents())
                .stream()
                .filter(word -> word.length() < getNgramUtilsConfig().getMinNgramLength())
                // either English morph analysis is off or the word is not an English stop word
                .filter(word -> !getNgramUtilsConfig().tryEnglishMorphAnalysis() || !EnglishUtils.stopWord(word))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // prefix ngrams are for high-priority search
        words.addAll(NgramUtils.createNgrams(text, NgramUtils.Mode.PREFIX, getNgramUtilsConfig()));
        String ngrams = String.join(" ", words);
        source.setFullTextSearchDataHighPriority(StringUtils.substring(ngrams, 0, MAX_FULL_TEXT_SEARCH_DATA_LENGTH));

        // infix ngrams are fow low-priority search
        ngrams = String.join(" ",
                NgramUtils.createNgrams(String.join(" ", text), NgramUtils.Mode.INFIX, getNgramUtilsConfig()));
        source.setFullTextSearchDataLowPriority(StringUtils.substring(ngrams, 0, MAX_FULL_TEXT_SEARCH_DATA_LENGTH));

        if (log.isTraceEnabled()) {
            log.trace("Set fullSearchDataHighPriority={}, fullSearchDataLowPriority={} for [{}] ID '{}'",
                    source.getFullTextSearchDataHighPriority(), source.getFullTextSearchDataLowPriority(),
                    source.getClass().getName(), source.getId());
        } else {
            log.info("Rebuilt FTS ngrams for [{}] ID '{}'", source.getClass().getName(), source.getId());
        }
    }

    public abstract NgramUtilsConfig getNgramUtilsConfig();

}
