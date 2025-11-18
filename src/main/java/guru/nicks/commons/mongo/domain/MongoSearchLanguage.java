package guru.nicks.commons.mongo.domain;

import jakarta.annotation.Nullable;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Valid languages for full-text search. Based on
 * <a href="https://docs.mongodb.com/manual/reference/text-search-languages">this document</a>.
 */
public enum MongoSearchLanguage {

    DA,
    DE,
    EN,
    ES,
    FI,
    FR,
    HU,
    IT,
    NB,
    NL,
    PT,
    RO,
    RU,
    SV,
    TR;

    public static final Set<MongoSearchLanguage> DEFAULT_SEARCH_LANGUAGE = EnumSet.of(EN);

    @Getter
    private static final Map<Locale, MongoSearchLanguage> LOCALE_TO_MONGO;

    static {
        LOCALE_TO_MONGO = Arrays.stream(values())
                .collect(Collectors.toUnmodifiableMap(
                        it -> Locale.forLanguageTag(it.name()), it -> it));
    }

    /**
     * Maps {@code Accept-Language} header value (its format is something like {@code ja,en;q=0.4}) to the members of
     * this enum. Locales not existing in the enum are skipped.
     *
     * @param headerValue header value (can be {@code null})
     * @return enum members (unmodifiable set) in the order of locale weights passed in the header;
     *         {@link #DEFAULT_SEARCH_LANGUAGE} if no locale matched or the header was missing/empty
     */
    public static Set<MongoSearchLanguage> parseAcceptLanguageHttpHeader(@Nullable String headerValue) {
        if (StringUtils.isBlank(headerValue)) {
            return DEFAULT_SEARCH_LANGUAGE;
        }

        List<Locale.LanguageRange> languagesByPriority = Locale.LanguageRange.parse(headerValue);
        List<Locale> localesByPriority = Locale.filter(languagesByPriority, LOCALE_TO_MONGO.keySet());

        Set<MongoSearchLanguage> languages = localesByPriority.stream()
                .map(LOCALE_TO_MONGO::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return languages.isEmpty()
                ? DEFAULT_SEARCH_LANGUAGE
                : languages;
    }

}
