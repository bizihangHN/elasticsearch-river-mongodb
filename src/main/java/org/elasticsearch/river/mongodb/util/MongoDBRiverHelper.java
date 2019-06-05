package org.elasticsearch.river.mongodb.util;

import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.elasticsearch.river.mongodb.MongoDBRiver;
import org.elasticsearch.river.mongodb.Status;

import java.io.IOException;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

/**
 * revier ES 操作工具
 */
@Slf4j
public abstract class MongoDBRiverHelper {
    /**
     * 获取mongo的状态
     *
     * @param client
     * @param riverName
     * @return
     */
    public static Status getRiverStatus(Client client, String riverName) {
        GetResponse statusResponse = client.prepareGet("_river", riverName, MongoDBRiver.STATUS_ID).get();
        if (!statusResponse.isExists()) {
            return Status.UNKNOWN;
        } else {
            Object obj = XContentMapValues.extractValue(MongoDBRiver.TYPE + "." + MongoDBRiver.STATUS_FIELD,
                    statusResponse.getSourceAsMap());
            return Status.valueOf(obj.toString());
        }
    }

    /**
     * 创建索引_river，并设置id为{@code _riverstatus}的document
     *
     * @param client
     * @param riverName
     * @param status
     */
    public static void setRiverStatus(Client client, String riverName, Status status) {
        log.info("setRiverStatus called with {} - {}", riverName, status);
        XContentBuilder xb;
        try {
            xb = jsonBuilder().startObject().startObject(MongoDBRiver.TYPE).field(MongoDBRiver.STATUS_FIELD, status).endObject()
                    .endObject();
            client.prepareIndex("_river", riverName, MongoDBRiver.STATUS_ID).setSource(xb).get();
        } catch (IOException ioEx) {
            log.error("setRiverStatus failed for river {}", ioEx, riverName);
        }
    }

}
