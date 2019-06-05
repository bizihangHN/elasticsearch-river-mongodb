package org.elasticsearch.river.mongodb.config;

import com.mongodb.BasicDBObject;
import com.mongodb.MongoClientOptions;
import com.mongodb.ServerAddress;
import lombok.Getter;
import lombok.Setter;
import org.elasticsearch.river.mongodb.RiverName;
import org.elasticsearch.river.mongodb.Timestamp;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author 毕子航 951755883@qq.com
 * @date 2019/06/04
 */
@Getter
@Setter
@Configuration
@EnableConfigurationProperties({RiverName.class})
@ConfigurationProperties(prefix = "river")
public class RiverProperties {
	// river
	private String riverName;
	private String riverIndexName;

	// mongodb.servers
	private List<ServerAddress> mongoServers = new ArrayList<>();

	// mongodb
	private String mongoDb;
	private String mongoCollection;
	private boolean mongoGridFS;
	private BasicDBObject mongoOplogFilter;
	private BasicDBObject mongoCollectionFilter;

	// mongodb.credentials
	private String mongoAdminUser;
	private String mongoAdminPassword;
	private String mongoAdminAuthDatabase;
	private String mongoLocalUser;
	private String mongoLocalPassword;
	private String mongoLocalAuthDatabase;

	// mongodb.options
	private MongoClientOptions mongoClientOptions;
	private int connectTimeout;
	private int socketTimeout;
	private boolean mongoSecondaryReadPreference;
	private boolean mongoUseSSL;
	private boolean mongoSSLVerifyCertificate;
	private boolean dropCollection;
	private Boolean isMongos;
	private Set<String> excludeFields;
	private Set<String> includeFields;
	private String includeCollection;
	private Timestamp<?> initialTimestamp;
	private String script;
	private String scriptType;
	private boolean advancedTransformation;
	private boolean skipInitialImport;
	private Set<String> parentTypes;
	private boolean storeStatistics;
	private String statisticsIndexName;
	private String statisticsTypeName;
	private boolean importAllCollections;
	private boolean disableIndexRefresh;

	// index,river索引的名字
	private String indexName;
	private String typeName;
	private int throttleSize;
}
