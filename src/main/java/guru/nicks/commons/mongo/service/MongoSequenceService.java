package guru.nicks.commons.mongo.service;

public interface MongoSequenceService {

    /**
     * Generates next value for the given sequence name.
     *
     * @param sequenceName sequence name (primary key in collection)
     * @return value, starting with 1
     */
    long generateNextValue(String sequenceName);

}
