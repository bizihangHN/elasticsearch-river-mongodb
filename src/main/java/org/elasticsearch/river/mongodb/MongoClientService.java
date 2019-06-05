package org.elasticsearch.river.mongodb;

import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.river.mongodb.config.RiverProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 获取mongoclient的服务
 *
 * @author 毕子航 951755883@qq.com
 * @date 2019/6/4
 */
@Slf4j
@Component
public class MongoClientService {
	@Autowired
	MongoClient mongoClient;


	public MongoClient getMongoClusterClient(RiverProperties definition) {
		return getMongoShardClient(definition, null);
	}

	/**
	 * Get or create a {@link MongoClient} for the given {@code servers}.
	 * <p>
	 * If a client already exists for the given list of servers with the same credentials and
	 * options it will be reused, otherwise a new client will be created.
	 */
	public MongoClient getMongoShardClient(RiverProperties definition, List<ServerAddress> shardServers) {
		return mongoClient;
	}
}
