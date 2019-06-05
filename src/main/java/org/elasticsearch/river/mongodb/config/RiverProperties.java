package org.elasticsearch.river.mongodb.config;

import com.mongodb.BasicDBObject;
import com.mongodb.MongoClientOptions;
import com.mongodb.ServerAddress;
import lombok.Getter;
import lombok.Setter;
import org.bson.types.BSONTimestamp;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.river.mongodb.MongoDBRiver;
import org.elasticsearch.river.mongodb.RiverName;
import org.elasticsearch.river.mongodb.Timestamp;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.elasticsearch.river.mongodb.MongoDBRiverConstant.*;

/**
 * 系统配置
 * @author 毕子航 951755883@qq.com
 * @date 2019/06/04
 */
@Getter
@Setter
@Configuration
@EnableConfigurationProperties({RiverName.class})
@ConfigurationProperties(prefix = "river")
public class RiverProperties {
	/**
	 * river
	 */
	private String riverName;
	private String riverIndexName;

	/**
	 *  mongodb.servers
	 */
	private List<ServerAddress> mongoServers = new ArrayList<>();

	/**
	 * mongodb
	 */
	private String mongoDb;
	private String mongoCollection;
	private boolean mongoGridFS = false;
	private BasicDBObject mongoOplogFilter;
	private BasicDBObject mongoCollectionFilter;

	/**
	 * mongodb.credentials
	 */
	private String mongoAdminUser;
	private String mongoAdminPassword;
	private String mongoAdminAuthDatabase;
	private String mongoLocalUser;
	private String mongoLocalPassword;
	private String mongoLocalAuthDatabase;

	/**
	 * mongodb.options
	 */
	private MongoClientOptions mongoClientOptions;
	private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;
	private int socketTimeout = DEFAULT_SOCKET_TIMEOUT;
	private boolean mongoSecondaryReadPreference = false;
	private boolean mongoUseSSL = false;
	private boolean mongoSSLVerifyCertificate = true;
	private boolean dropCollection = false;
	private Boolean isMongos = true;
	private Set<String> excludeFields;
	private Set<String> includeFields;
	private String includeCollection = "";
	private Timestamp<?> initialTimestamp;
	private String script;
	private String scriptType;
	private boolean advancedTransformation = false;
	private boolean skipInitialImport = false;
	private Set<String> parentTypes;
	private boolean storeStatistics = false;
	private String statisticsIndexName;
	private String statisticsTypeName;
	private boolean importAllCollections = false;
	private boolean disableIndexRefresh = false;

	/**
	 * index,river索引的名字
	 */
	private String indexName;
	private String typeName;
	private int throttleSize = DEFAULT_BULK_ACTIONS * 5;

	private int connectionsPerHost = DEFAULT_CONNECTIONS_PER_HOST;
	private int threadsAllowedToBlockForConnectionMultiplier = DEFAULT_THREADS_ALLOWED_TO_BLOCK_FOR_CONNECTION_MULTIPLIER;

	private Bulk bulk;

	@Getter
	@Setter
	public class Bulk {
		private int concurrentRequests = DEFAULT_CONCURRENT_REQUESTS;
		private int bulkActions = DEFAULT_BULK_ACTIONS;
		private ByteSizeValue bulkSize = DEFAULT_BULK_SIZE;
		private TimeValue flushInterval = DEFAULT_FLUSH_INTERVAL;
	}

	public void setIncludeFields(Set<String> includeFields) {
		this.includeFields = includeFields;
		if (!includeFields.contains(MongoDBRiver.MONGODB_ID_FIELD)) {
			includeFields.add(MongoDBRiver.MONGODB_ID_FIELD);
		}
	}

	public void setInitialTimestamp(Timestamp<?> initialTimestamp) {
		BSONTimestamp bsonTimestamp = new BSONTimestamp((int) (new Date(initialTimestamp.getTime()).getTime() / 1000), 1);
		this.initialTimestamp = new Timestamp.BSON(bsonTimestamp);
	}

	public void setMongoCollectionFilter(String filter) {
		if (!StringUtils.hasText(filter)) {
			filter = removePrefix("o.", filter);
			this.mongoCollectionFilter = convertToBasicDBObject(filter);
			this.mongoCollectionFilter = convertToBasicDBObject(filter);
		}
	}

	public String getMongoOplogNamespace() {
		return getMongoDb() + "." + getMongoCollection();
	}
}
