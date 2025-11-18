package guru.nicks.commons.mongo.service;

public interface MongoSequenceService {

    String USER_ID_SEQUENCE = "userId";
    String ORDER_ID_SEQUENCE = "orderId";
    String PRODUCT_ID_SEQUENCE = "productId";

    /**
     * Generates next value for the given sequence name.
     *
     * @param sequenceName sequence name, such as {@value #USER_ID_SEQUENCE}
     * @return value, starting with 1
     */
    long generateNextValue(String sequenceName);

}
