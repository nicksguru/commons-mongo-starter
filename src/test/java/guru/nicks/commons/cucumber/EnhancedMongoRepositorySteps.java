package guru.nicks.commons.cucumber;

import guru.nicks.commons.mongo.repository.EnhancedMongoRepository;
import guru.nicks.commons.utils.ExceptionUtils;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.experimental.StandardException;
import org.springframework.data.domain.Persistable;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Step definitions for exception construction in {@link EnhancedMongoRepository#getById(Object)} testing: the
 * construction path is memoized per exception class, but every miss must produce a fresh exception instance.
 * <p>
 * The repository fake is a Mockito mock with {@code CALLS_REAL_METHODS}: {@code getById} and generic resolution execute
 * real default methods while {@code findById} stays mocked, so no MongoDB is needed.
 */
public class EnhancedMongoRepositorySteps {

    private ValidRepository repository;
    private String missingId;

    private GoodException firstThrownException;
    private GoodException secondThrownException;

    @Given("a repository that finds no document by ID {string}")
    public void aRepositoryThatFindsNoDocumentById(String missingId) {
        this.missingId = missingId;

        repository = mock(ValidRepository.class, CALLS_REAL_METHODS);
        when(repository.findById(missingId)).thenReturn(Optional.empty());
    }

    @When("getById is called twice for the same missing ID")
    public void getByIdIsCalledTwiceForTheSameMissingId() {
        // two consecutive misses: same exception type, distinct instances
        firstThrownException = (GoodException) catchThrowable(() -> repository.getById(missingId));
        secondThrownException = (GoodException) catchThrowable(() -> repository.getById(missingId));
    }

    @Then("each call should throw the repository exception")
    public void eachCallShouldThrowTheRepositoryException() {
        assertThat(firstThrownException)
                .as("first thrown exception")
                .isNotNull()
                .isInstanceOf(GoodException.class);

        assertThat(secondThrownException)
                .as("second thrown exception")
                .isNotNull()
                .isInstanceOf(GoodException.class);
    }

    @Then("the thrown exceptions should be distinct instances")
    public void theThrownExceptionsShouldBeDistinctInstances() {
        // the memoization caches the construction path, never the instance
        assertThat(secondThrownException)
                .as("second thrown exception")
                .isNotSameAs(firstThrownException);
    }

    /**
     * Non-generic subinterface: generics are materialized here exactly like in user repositories.
     */
    interface ValidRepository extends EnhancedMongoRepository<TestDoc, String, GoodException, Void> {
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
     * According to {@link ExceptionUtils#getExceptionFactory(Class)}, the class must have a public constructor with a
     * cause parameter.
     */
    @StandardException
    public static class GoodException extends RuntimeException {
    }

}
