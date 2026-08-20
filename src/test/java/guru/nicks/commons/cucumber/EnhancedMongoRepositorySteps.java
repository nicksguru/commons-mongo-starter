package guru.nicks.commons.cucumber;

import guru.nicks.commons.mongo.repository.EnhancedMongoRepository;
import guru.nicks.commons.mongo.repository.MemoizedExceptionSuppliers;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.data.domain.Persistable;

import java.util.Optional;
import java.util.function.Supplier;

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

    private Supplier<GoodException> memoizedSupplier;
    private Supplier<GoodException> anotherMemoizedSupplier;

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

    @When("a memoized exception supplier is obtained for the repository exception class")
    public void aMemoizedExceptionSupplierIsObtainedForTheRepositoryExceptionClass() {
        memoizedSupplier = MemoizedExceptionSuppliers.getSupplierFor(GoodException.class);
    }

    @When("the supplier is obtained again")
    public void theSupplierIsObtainedAgain() {
        anotherMemoizedSupplier = MemoizedExceptionSuppliers.getSupplierFor(GoodException.class);
    }

    @Then("both suppliers should be the same instance")
    public void bothSuppliersShouldBeTheSameInstance() {
        // same construction path per exception class...
        assertThat(anotherMemoizedSupplier)
                .as("repeatedly obtained supplier")
                .isSameAs(memoizedSupplier);
    }

    @Then("each supplier call should produce a distinct exception instance")
    public void eachSupplierCallShouldProduceADistinctExceptionInstance() {
        // ...producing a new exception instance on each call
        assertThat(memoizedSupplier.get())
                .as("freshly supplied exception")
                .isNotSameAs(memoizedSupplier.get());
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
     * Exception with an argumentless constructor.
     */
    static class GoodException extends RuntimeException {
    }

}
