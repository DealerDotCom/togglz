package org.togglz.dynamodb;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.togglz.core.Feature;
import org.togglz.core.repository.FeatureState;
import org.togglz.core.repository.StateRepository;
import org.togglz.core.util.FeatureStateStorageWrapper;

import java.io.IOException;

/**
 * A state repository that uses Amazon's DynamoDB.
 *
 * The repository is configured using the {@link DynamoDBStateRepositoryBuilder}
 *
 * You must already have a table provisioned before you create this repository.
 *
 * @author Ryan Gardner
 * @date 8/26/16
 */
public class DynamoDBStateRepository implements StateRepository {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DynamoDBStateRepository.class);

    private ObjectMapper objectMapper = new ObjectMapper();
    private Table table;

    private String primaryKeyAttribute;
    public static final String FEATURE_STATE_ATTRIBUTE_NAME = "featureState";


    private AmazonDynamoDBClient amazonDynamoDBClient;

    private DynamoDBStateRepository(DynamoDBStateRepositoryBuilder builder) {
        this.objectMapper = builder.objectMapper;
        this.table = builder.table;
        this.primaryKeyAttribute = builder.primaryKey;

        TableDescription tableDescription = table.describe();
        log.info("Creating DynamoDBStateRepository with table named: {}", table.getTableName());
        log.info("Table description: {}", tableDescription.toString());
    }


    @Override
    public FeatureState getFeatureState(Feature feature) {
        Item documentItem = table.getItem(new GetItemSpec().withPrimaryKey(primaryKeyAttribute, feature.name()).withAttributesToGet(FEATURE_STATE_ATTRIBUTE_NAME));
        try {
            FeatureStateStorageWrapper wrapper = objectMapper.reader().forType(FeatureStateStorageWrapper.class).readValue(documentItem.getJSON(FEATURE_STATE_ATTRIBUTE_NAME));
            return FeatureStateStorageWrapper.featureStateForWrapper(feature, wrapper);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't parse the feature state", e);
        }
    }

    @Override
    public void setFeatureState(FeatureState featureState) {
        try {
            String json = objectMapper.writeValueAsString(FeatureStateStorageWrapper.wrapperForFeatureState(featureState));
            Item featureStateEntry =
                    new Item()
                            .withPrimaryKey(primaryKeyAttribute, featureState.getFeature().name())
                            .withJSON(FEATURE_STATE_ATTRIBUTE_NAME, json);
            table.putItem(featureStateEntry);

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Unable to serialize the feature state", e);
        }
    }

    /**
     * <p>
     * Builder for a DynamoDBStateRepository.
     * </p>
     * <p>
     * <p>
     * Usage example:
     * </p>
     * <p>
     * <pre>
     * DynamoDBStateRepository dynamoDbStateRepository = new DynamoDBStateRepositoryBuilder(dbClient)
     *     .withStateStoredInTable(&quot;togglz-state-storage&quot;)
     *     .build();
     * </pre>
     *
     * @author ryan.gardner@dealer.com
     */
    public static class DynamoDBStateRepositoryBuilder {
        private String tableName = "togglz";
        private AmazonDynamoDBClient amazonDynamoDBClient;
        private ObjectMapper objectMapper;
        private String primaryKey = "featureName";
        private Table table;
        private DynamoDB dynamoDB;


        public DynamoDBStateRepositoryBuilder(AmazonDynamoDBClient dbClient) {
            this.amazonDynamoDBClient = dbClient;
        }

        public DynamoDBStateRepositoryBuilder withObjectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        public DynamoDBStateRepositoryBuilder withStateStoredInTable(String tableName) {
            this.tableName = tableName;
            return this;
        }

        public DynamoDBStateRepository build() {
            this.dynamoDB = new DynamoDB(this.amazonDynamoDBClient);
            initializeObjectMapper();
            initializeTable();
            return new DynamoDBStateRepository(this);
        }

        private void initializeTable() {
            this.table = dynamoDB.getTable(this.tableName);
        }

        // create a new object mapper if one wasn't passed into the builder
        private void initializeObjectMapper() {
            if (this.objectMapper == null) {
                this.objectMapper = new ObjectMapper();
            }
        }


    }
}
