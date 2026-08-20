package guru.nicks.commons.cucumber;

import guru.nicks.commons.cucumber.world.TextWorld;
import guru.nicks.commons.mongo.config.EnhancedMongoRepositoryValidator;
import guru.nicks.commons.mongo.repository.EnhancedMongoRepository;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Persistable;
import org.springframework.data.repository.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Step definitions for {@link EnhancedMongoRepositoryValidator} testing.
 * <p>
 * Repository fakes are Mockito mocks with {@code CALLS_REAL_METHODS}: default methods (generic resolution) execute real
 * code while abstract methods stay mocked, so no MongoDB and no handwritten stubs are needed.
 */
@RequiredArgsConstructor
public class EnhancedMongoRepositoryValidatorSteps {

    // DI
    private final TextWorld textWorld;

    private Collection<?> repositories = List.of();
    private ApplicationContext applicationContext;
    private Repository<?, ?> plainRepository;

    @Given("a valid enhanced repository")
    public void aValidEnhancedRepository() {
        repositories = List.of(mock(ValidRepository.class, CALLS_REAL_METHODS));
    }

    @Given("an enhanced repository whose exception class has no argumentless constructor")
    public void anEnhancedRepositoryWhoseExceptionClassHasNoArgumentlessConstructor() {
        repositories = List.of(mock(BadConstructorRepository.class, CALLS_REAL_METHODS));
    }

    @Given("an enhanced repository with unmaterialized generics")
    public void anEnhancedRepositoryWithUnmaterializedGenerics() {
        repositories = List.of(mock(UnmaterializedRepository.class, CALLS_REAL_METHODS));
    }

    @Given("a plain Spring Data repository")
    public void aPlainSpringDataRepository() {
        plainRepository = mock(PlainRepository.class);
        repositories = List.of(plainRepository);
    }

    @Given("an application context containing a valid enhanced repository bean")
    public void anApplicationContextContainingAValidEnhancedRepositoryBean() {
        var context = mock(ApplicationContext.class);
        var repository = mock(ValidRepository.class, CALLS_REAL_METHODS);
        doReturn(Map.of("validRepository", repository)).when(context).getBeansOfType(Repository.class);

        applicationContext = context;
    }

    @When("repositories are validated")
    public void repositoriesAreValidated() {
        textWorld.setLastException(catchThrowable(() -> EnhancedMongoRepositoryValidator.validate(repositories)));
    }

    @When("the application context is validated")
    public void theApplicationContextIsValidated() {
        textWorld.setLastException(catchThrowable(() -> EnhancedMongoRepositoryValidator.validate(applicationContext)));
    }

    @Then("validation should pass")
    public void validationShouldPass() {
        assertThat(textWorld.getLastException())
                .as("lastException")
                .isNull();
    }

    @Then("validation should fail with {string}")
    public void validationShouldFailWith(String expectedMessagePart) {
        assertThat(textWorld.getLastException())
                .as("lastException")
                .isNotNull()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(expectedMessagePart);
    }

    @Then("the error message should contain the exception class name")
    public void theErrorMessageShouldContainTheExceptionClassName() {
        assertThat(textWorld.getLastException())
                .as("lastException message")
                .hasMessageContaining(NoDefaultCtorException.class.getName());
    }

    @Then("the error message should contain the repository interface name")
    public void theErrorMessageShouldContainTheRepositoryInterfaceName() {
        assertThat(textWorld.getLastException())
                .as("lastException message")
                .hasMessageContaining(UnmaterializedRepository.class.getName());
    }

    @Then("the plain repository should have no interactions")
    public void thePlainRepositoryShouldHaveNoInteractions() {
        verifyNoInteractions(plainRepository);
    }

    /**
     * Non-generic subinterface: generics are materialized here exactly like in user repositories.
     */
    interface ValidRepository extends EnhancedMongoRepository<TestDoc, String, GoodException, Void> {
    }

    /**
     * Exception class has no argumentless constructor.
     */
    interface BadConstructorRepository extends EnhancedMongoRepository<TestDoc, String, NoDefaultCtorException, Void> {
    }

    /**
     * Generic subinterface: {@code E} stays a type variable, so it can't be materialized at runtime.
     */
    interface UnmaterializedRepository<E extends RuntimeException>
            extends EnhancedMongoRepository<TestDoc, String, E, Void> {
    }

    /**
     * Plain Spring Data repository, not an enhanced one.
     */
    interface PlainRepository extends Repository<Object, String> {
    }

    /**
     * Minimal document satisfying {@code T extends Persistable<ID>}.
     */
    record TestDoc(

            String id) implements Persistable<String> {

        @Override
        public String getId() {
            return id;
        }

        @Override
        public boolean isNew() {
            return true;
        }

    }

    /**
     * Exception with an argumentless constructor.
     */
    static class GoodException extends RuntimeException {
    }

    /**
     * Exception whose only constructor takes an argument.
     */
    static class NoDefaultCtorException extends RuntimeException {

        NoDefaultCtorException(String message) {
            super(message);
        }

    }

}
