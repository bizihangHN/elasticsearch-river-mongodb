package org.elasticsearch.river.mongodb.config;

import com.mongodb.MongoClientOptions;
import com.mongodb.ReadPreference;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.river.mongodb.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

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
	MongoDBRiver mongoDBRiver(RiverName riverName, Client transportClient, MongoClientService mongoClientService) {
		Settings build = Settings.builder().put("a", "b").build();
		RiverSettings riverSettings = new RiverSettings(build, Collections.EMPTY_MAP);
		MongoDBRiver mongoDBRiver = new MongoDBRiver(riverName, riverSettings, "default_name", transportClient, mongoClientService);
		return mongoDBRiver;
	}
}
