package org.elasticsearch.river.mongodb;

import org.elasticsearch.common.settings.Settings;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * @author 毕子航 951755883@qq.com
 * @date 2019/06/04
 */
public class RiverSettings {
	private final Settings globalSettings;

	private final Map<String, Object> settings;

	public RiverSettings(Settings globalSettings, Map<String, Object> settings) {
		this.globalSettings = globalSettings;
		this.settings = settings;
	}

	public Settings globalSettings() {
		return globalSettings;
	}

	public Map<String, Object> settings() {
		return settings;
	}
}
