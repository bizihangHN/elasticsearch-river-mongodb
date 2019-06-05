package org.elasticsearch.river.mongodb;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 应用的名字
 *
 * @author 毕子航 951755883@qq.com
 * @date 2019/06/04
 */
@Configuration
@EnableConfigurationProperties({RiverName.class})
@ConfigurationProperties(prefix = "river")
public class RiverName {
	private String name;

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public String name() {
		return name;
	}
}
