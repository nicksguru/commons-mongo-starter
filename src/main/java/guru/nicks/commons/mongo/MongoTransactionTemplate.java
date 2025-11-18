package guru.nicks.commons.mongo;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Autowire this by class and/or bean name in order to distinguish from other DB engines' transaction templates.
 */
public class MongoTransactionTemplate extends TransactionTemplate {

    public MongoTransactionTemplate(PlatformTransactionManager transactionManager) {
        super(transactionManager);
    }

}
