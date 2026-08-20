package guru.nicks.commons.mongo.config;

import guru.nicks.commons.mongo.repository.EnhancedMongoRepository;

import lombok.experimental.UtilityClass;
import org.springframework.context.ApplicationContext;
import org.springframework.data.repository.Repository;

import java.util.Arrays;
import java.util.Collection;

/**
 * Fail-fast startup validation for {@link EnhancedMongoRepository} beans. What previously failed only at the first
 * {@link EnhancedMongoRepository#getById(Object)} miss - unmaterialized generics or a missing argumentless constructor
 * in the exception class - is verified eagerly during context refresh. Pure reflection on repository interfaces, no
 * database access. {@link EnhancedMongoRepository#convertToSearchPredicate(Object)} needs no check: it's abstract and
 * enforced by Spring Data query derivation at startup.
 */
@UtilityClass
public class EnhancedMongoRepositoryValidator {

    /**
     * Validates every {@link Repository} bean found in the given context. No-op when no enhanced repositories exist.
     *
     * @param applicationContext application context to look up repository beans in
     * @throws IllegalStateException an enhanced repository failed validation
     */
    public static void validate(ApplicationContext applicationContext) {
        validate(applicationContext.getBeansOfType(Repository.class).values());
    }

    /**
     * Validates all enhanced repositories among the given beans; non-enhanced {@link Repository} beans are ignored.
     *
     * @param beans repository beans (possibly proxies)
     * @throws IllegalStateException an enhanced repository failed validation
     */
    public static void validate(Collection<?> beans) {
        for (Object bean : beans) {
            // skip non-enhanced repositories - nothing to validate; the check is proxy-safe because repository
            // proxies implement the user's repository interface
            if (bean instanceof EnhancedMongoRepository<?, ?, ?, ?> repository) {
                validateRepository(repository, findRepositoryInterface(bean.getClass()));
            }
        }
    }

    /**
     * Exercises the same reflection the runtime path uses - resolving document and exception classes fails on
     * unmaterialized generics - and verifies the exception class has an argumentless constructor.
     *
     * @param repository          enhanced repository (possibly a proxy)
     * @param repositoryInterface user-facing repository interface, for error messages
     * @throws IllegalStateException generics are not materialized, or the exception class has no argumentless
     *                               constructor
     */
    static void validateRepository(EnhancedMongoRepository<?, ?, ?, ?> repository, Class<?> repositoryInterface) {
        Class<?> exceptionClass;
        try {
            repository.getDocumentClass();
            exceptionClass = repository.getExceptionClass();
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to resolve document/exception class in repository ["
                    + repositoryInterface.getName() + "]: " + e.getMessage(), e);
        }

        try {
            exceptionClass.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Can't find argumentless constructor for exception class ["
                    + exceptionClass.getName() + "]: " + e.getMessage(), e);
        }
    }

    /**
     * Finds the user-facing repository interface implemented by the given (possibly proxy) class, so that error
     * messages name the user's interface and not a generated proxy class.
     *
     * @param beanClass repository bean class (possibly a JDK/CGLIB proxy class)
     * @return interface extending {@link EnhancedMongoRepository}, or the class itself if none found
     */
    private static Class<?> findRepositoryInterface(Class<?> beanClass) {
        // proxies implement the user's interface directly; plain classes may implement it via a superclass
        return Arrays.stream(beanClass.getInterfaces())
                .filter(EnhancedMongoRepository.class::isAssignableFrom)
                .findFirst()
                .orElse(beanClass);
    }

}
