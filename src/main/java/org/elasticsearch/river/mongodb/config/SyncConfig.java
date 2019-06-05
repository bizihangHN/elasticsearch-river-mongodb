package org.elasticsearch.river.mongodb.config;

import com.mongodb.MongoClientOptions;
import com.mongodb.ReadPreference;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Client;
import org.elasticsearch.river.mongodb.MongoClientService;
import org.elasticsearch.river.mongodb.MongoDBRiver;
import org.elasticsearch.river.mongodb.MongoDBRiverConstant;
import org.elasticsearch.river.mongodb.RiverName;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author 毕子航 951755883@qq.com
 * @date 2019/06/04
 */
@Slf4j
@Configuration
public class SyncConfig {
	@Bean
	MongoClientOptions mongoClientOptions(RiverProperties riverProperties) {
		MongoClientOptions.Builder mongoClientOptionsBuilder = MongoClientOptions.builder().socketKeepAlive(true);

		//设置mongoClientOptions
		mongoClientOptionsBuilder
				.connectTimeout(riverProperties.getConnectTimeout())
				.socketTimeout(riverProperties.getSocketTimeout())
				.connectionsPerHost(riverProperties.getConnectionsPerHost())
				.threadsAllowedToBlockForConnectionMultiplier(riverProperties.getThreadsAllowedToBlockForConnectionMultiplier());

		if (riverProperties.isMongoSecondaryReadPreference()) {
			mongoClientOptionsBuilder.readPreference(ReadPreference.secondaryPreferred());
		}

		if (riverProperties.isMongoUseSSL()) {
			mongoClientOptionsBuilder.socketFactory(MongoDBRiverConstant.getSSLSocketFactory());
		}

		return mongoClientOptionsBuilder.build();
	}

	@Bean
	MongoDBRiver mongoDBRiver(RiverName riverName, Client transportClient, MongoClientService mongoClientService, RiverProperties riverProperties) {
		MongoDBRiver mongoDBRiver = new MongoDBRiver(riverName, null, transportClient, riverProperties, mongoClientService);
		return mongoDBRiver;
	}
}
