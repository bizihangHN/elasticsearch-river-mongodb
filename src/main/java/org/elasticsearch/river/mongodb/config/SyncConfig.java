package org.elasticsearch.river.mongodb.config;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.river.mongodb.MongoClientService;
import org.elasticsearch.river.mongodb.MongoDBRiver;
import org.elasticsearch.river.mongodb.RiverName;
import org.elasticsearch.river.mongodb.RiverSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

/**
 * @author 毕子航 951755883@qq.com
 * @date 2019/06/04
 */
@Configuration
public class SyncConfig {

	@Bean
	MongoDBRiver mongoDBRiver(RiverName riverName, Client transportClient, MongoClientService mongoClientService) {
		Settings build = Settings.builder().put("a", "b").build();
		RiverSettings riverSettings = new RiverSettings(build, Collections.EMPTY_MAP);
		MongoDBRiver mongoDBRiver = new MongoDBRiver(riverName, riverSettings, "default_name", transportClient, mongoClientService);
		return mongoDBRiver;
	}
}
