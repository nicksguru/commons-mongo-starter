Feature: Enhanced Mongo Repository Validator
  Fail-fast startup validation for enhanced Mongo repositories: unmaterialized generics and missing argumentless
  exception constructors are detected eagerly, without MongoDB.

  Scenario: Valid repository passes validation
    Given a valid enhanced repository
    When repositories are validated
    Then validation should pass

  Scenario: Exception class without argumentless constructor fails validation
    Given an enhanced repository whose exception class has no argumentless constructor
    When repositories are validated
    Then validation should fail with "Can't find argumentless constructor for exception class"
    And the error message should contain the exception class name

  Scenario: Unmaterialized generics fail validation naming the repository
    Given an enhanced repository with unmaterialized generics
    When repositories are validated
    Then validation should fail with "Failed to resolve document/exception class in repository"
    And the error message should contain the repository interface name

  Scenario: Non-enhanced repositories are ignored
    Given a plain Spring Data repository
    When repositories are validated
    Then validation should pass
    And the plain repository should have no interactions

  Scenario: All repository beans from the application context are validated
    Given an application context containing a valid enhanced repository bean
    When the application context is validated
    Then validation should pass
