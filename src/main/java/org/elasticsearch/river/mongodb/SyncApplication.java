package org.elasticsearch.river.mongodb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author 毕子航 951755883@qq.com
 * @date 2019/06/04
 */
@SpringBootApplication
public class SyncApplication {
	public static void main(String[] args) {
		SpringApplication springApplication = new SpringApplication(SyncApplication.class);
		springApplication.run();
	}
}
