Feature: Enhanced Mongo Repository
  Exception construction on getById() misses: the construction path is memoized per exception class, but every miss
  produces a fresh exception instance.

  Scenario: getById throws a fresh exception instance on each miss
    Given a repository that finds no document by ID "missing"
    When getById is called twice for the same missing ID
    Then each call should throw the repository exception
    And the thrown exceptions should be distinct instances

  Scenario: Construction path is memoized but instances are fresh
    When a memoized exception supplier is obtained for the repository exception class
    And the supplier is obtained again
    Then both suppliers should be the same instance
    And each supplier call should produce a distinct exception instance
