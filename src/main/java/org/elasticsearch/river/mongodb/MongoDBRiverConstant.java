package org.elasticsearch.river.mongodb;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.util.JSON;
import org.bson.BasicBSONObject;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * 系统常量
 *
 * @author 毕子航 951755883@qq.com
 * @date 2019/6/5
 */
public abstract class MongoDBRiverConstant {
	public static final Logger log = LoggerFactory.getLogger(MongoDBRiverConstant.class);

	// defaults
	public final static String DEFAULT_DB_HOST = "localhost";
	public final static int DEFAULT_DB_PORT = 27017;
	public final static int DEFAULT_CONCURRENT_REQUESTS = Runtime.getRuntime().availableProcessors();
	public final static int DEFAULT_BULK_ACTIONS = 1000;
	public final static TimeValue DEFAULT_FLUSH_INTERVAL = TimeValue.timeValueMillis(10);
	public final static ByteSizeValue DEFAULT_BULK_SIZE = new ByteSizeValue(5, ByteSizeUnit.MB);
	public final static int DEFAULT_CONNECT_TIMEOUT = 30000;
	public final static int DEFAULT_SOCKET_TIMEOUT = 60000;
	public final static int DEFAULT_CONNECTIONS_PER_HOST = 100;
	public final static int DEFAULT_THREADS_ALLOWED_TO_BLOCK_FOR_CONNECTION_MULTIPLIER = 5;

	// fields
	public final static String DB_FIELD = "db";
	public final static String SERVERS_FIELD = "servers";
	public final static String HOST_FIELD = "host";
	public final static String PORT_FIELD = "port";
	public final static String OPTIONS_FIELD = "options";
	public final static String SECONDARY_READ_PREFERENCE_FIELD = "secondary_read_preference";
	public final static String CONNECT_TIMEOUT = "connect_timeout";
	public final static String SOCKET_TIMEOUT = "socket_timeout";
	public final static String SSL_CONNECTION_FIELD = "ssl";
	public final static String SSL_VERIFY_CERT_FIELD = "ssl_verify_certificate";
	public final static String IS_MONGOS_FIELD = "is_mongos";
	public final static String DROP_COLLECTION_FIELD = "drop_collection";
	public final static String EXCLUDE_FIELDS_FIELD = "exclude_fields";
	public final static String INCLUDE_FIELDS_FIELD = "include_fields";
	public final static String INCLUDE_COLLECTION_FIELD = "include_collection";
	public final static String INITIAL_TIMESTAMP_FIELD = "initial_timestamp";
	public final static String INITIAL_TIMESTAMP_SCRIPT_TYPE_FIELD = "script_type";
	public final static String INITIAL_TIMESTAMP_SCRIPT_FIELD = "script";
	public final static String ADVANCED_TRANSFORMATION_FIELD = "advanced_transformation";
	public final static String SKIP_INITIAL_IMPORT_FIELD = "skip_initial_import";
	public final static String CONNECTIONS_PER_HOST = "connections_per_host";
	public final static String THREADS_ALLOWED_TO_BLOCK_FOR_CONNECTION_MULTIPLIER = "threads_allowed_to_block_for_connection_multiplier";
	public final static String PARENT_TYPES_FIELD = "parent_types";
	public final static String STORE_STATISTICS_FIELD = "store_statistics";
	public final static String IMPORT_ALL_COLLECTIONS_FIELD = "import_all_collections";
	public final static String DISABLE_INDEX_REFRESH_FIELD = "disable_index_refresh";
	public final static String FILTER_FIELD = "filter";
	public final static String CREDENTIALS_FIELD = "credentials";
	public final static String USER_FIELD = "user";
	public final static String PASSWORD_FIELD = "password";
	public final static String AUTH_FIELD = "auth";
	public final static String SCRIPT_FIELD = "script";
	public final static String SCRIPT_TYPE_FIELD = "script_type";
	public final static String COLLECTION_FIELD = "collection";
	public final static String GRIDFS_FIELD = "gridfs";
	public final static String INDEX_OBJECT = "index";
	public final static String NAME_FIELD = "name";
	public final static String TYPE_FIELD = "type";
	public final static String LOCAL_DB_FIELD = "local";
	public final static String ADMIN_DB_FIELD = "admin";
	public final static String THROTTLE_SIZE_FIELD = "throttle_size";
	public final static String BULK_SIZE_FIELD = "bulk_size";
	public final static String BULK_TIMEOUT_FIELD = "bulk_timeout";
	public final static String CONCURRENT_BULK_REQUESTS_FIELD = "concurrent_bulk_requests";

	public final static String BULK_FIELD = "bulk";
	public final static String ACTIONS_FIELD = "actions";
	public final static String SIZE_FIELD = "size";
	public final static String CONCURRENT_REQUESTS_FIELD = "concurrent_requests";
	public final static String FLUSH_INTERVAL_FIELD = "flush_interval";

	public static SocketFactory getSSLSocketFactory() {
		SocketFactory sslSocketFactory;
		try {
			final TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {

				@Override
				public X509Certificate[] getAcceptedIssuers() {
					return null;
				}

				@Override
				public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
				}

				@Override
				public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
				}
			}};
			final SSLContext sslContext = SSLContext.getInstance("SSL");
			sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
			// Create an ssl socket factory with our all-trusting manager
			sslSocketFactory = sslContext.getSocketFactory();
			return sslSocketFactory;
		} catch (Exception ex) {
			log.warn("Unable to build ssl socket factory without certificate validation, using default instead.", ex);
		}
		return SSLSocketFactory.getDefault();
	}

	public static BasicDBObject convertToBasicDBObject(String object) {
		if (object == null || object.length() == 0) {
			return new BasicDBObject();
		} else {
			return (BasicDBObject) JSON.parse(object);
		}
	}

	public static String removePrefix(String prefix, String object) {
		return addRemovePrefix(prefix, object, false);
	}

	public static String addRemovePrefix(String prefix, String object, boolean add) {
		if (prefix == null) {
			throw new IllegalArgumentException("prefix");
		}
		if (object == null) {
			throw new NullPointerException("object");
		}
		if (object.length() == 0) {
			return "";
		}
		DBObject bsonObject = (DBObject) JSON.parse(object);

		BasicBSONObject newObject = new BasicBSONObject();
		for (String key : bsonObject.keySet()) {
			if (add) {
				newObject.put(prefix + key, bsonObject.get(key));
			} else {
				if (key.startsWith(prefix)) {
					newObject.put(key.substring(prefix.length()), bsonObject.get(key));
				} else {
					newObject.put(key, bsonObject.get(key));
				}
			}
		}
		return newObject.toString();
	}
}
