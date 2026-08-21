Feature: Enhanced Mongo Repository
  Exception construction on getById() misses: the construction path is memoized per exception class, but every miss
  produces a fresh exception instance.

  Scenario: getById throws a fresh exception instance on each miss
    Given a repository that finds no document by ID "missing"
    When getById is called twice for the same missing ID
    Then each call should throw the repository exception
    And the thrown exceptions should be distinct instances
