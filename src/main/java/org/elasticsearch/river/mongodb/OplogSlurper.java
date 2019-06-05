package org.elasticsearch.river.mongodb;

import com.google.common.collect.ImmutableList;
import com.mongodb.*;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.gridfs.GridFSFile;
import com.mongodb.util.JSONSerializers;
import lombok.extern.slf4j.Slf4j;
import org.bson.BasicBSONObject;
import org.bson.types.ObjectId;
import org.elasticsearch.client.Client;
import org.elasticsearch.river.mongodb.util.MongoDBHelper;
import org.elasticsearch.river.mongodb.util.MongoDBRiverHelper;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
class OplogSlurper implements Runnable {

    class SlurperException extends Exception {

        private static final long serialVersionUID = 1L;

        SlurperException(String message) {
            super(message);
        }
    }

    private final MongoDBRiver river;
    private final MongoDBRiverDefinition definition;
    private final SharedContext context;
    private final BasicDBObject findKeys;
    private final String gridfsOplogNamespace;
    private final String cmdOplogNamespace;
    /**
     * from TokuMX
     */
    private final ImmutableList<String> oplogOperations = ImmutableList.of(MongoDBRiver.OPLOG_DELETE_OPERATION, MongoDBRiver.OPLOG_UPDATE_ROW_OPERATION,
            MongoDBRiver.OPLOG_UPDATE_OPERATION, MongoDBRiver.OPLOG_INSERT_OPERATION, MongoDBRiver.OPLOG_COMMAND_OPERATION);
    private final Client esClient;
    private final MongoClient mongoClusterClient;
    private final MongoClient mongoShardClient;
    private Timestamp<?> timestamp;
    private final DB slurpedDb;
    private final DB oplogDb;
    private final DBCollection oplogCollection, oplogRefsCollection;
    private final AtomicLong totalDocuments = new AtomicLong();

    public OplogSlurper(MongoDBRiver river, Timestamp<?> timestamp, MongoClient mongoClusterClient, MongoClient mongoShardClient) {
        this.river = river;
        this.timestamp = timestamp;
        this.definition = river.definition;
        this.context = river.context;
        this.esClient = river.esClient;
        this.mongoClusterClient = mongoClusterClient;
        this.mongoShardClient = mongoShardClient;
        this.findKeys = new BasicDBObject();
        this.gridfsOplogNamespace = definition.getMongoOplogNamespace() + MongoDBRiver.GRIDFS_FILES_SUFFIX;
        this.cmdOplogNamespace = definition.getMongoDb() + "." + MongoDBRiver.OPLOG_NAMESPACE_COMMAND;
        if (definition.getExcludeFields() != null) {
            for (String key : definition.getExcludeFields()) {
                findKeys.put(key, 0);
            }
        } else if (definition.getIncludeFields() != null) {
            for (String key : definition.getIncludeFields()) {
                findKeys.put(key, 1);
            }
        }
        this.oplogDb = mongoShardClient.getDB(MongoDBRiver.MONGODB_LOCAL_DATABASE);
        this.oplogCollection = oplogDb.getCollection(MongoDBRiver.OPLOG_COLLECTION);
        this.oplogRefsCollection = oplogDb.getCollection(MongoDBRiver.OPLOG_REFS_COLLECTION);
        this.slurpedDb = mongoShardClient.getDB(definition.getMongoDb());
    }

    @Override
    public void run() {
        //如果程序是运行状态，那就一直运行
        while (context.getStatus() == Status.RUNNING) {
            try {        
                // Slurp from oplog
                DBCursor cursor = null;
                try {
                    //获取某个时间点以后的数据
                    cursor = oplogCursor(timestamp);
                    if (cursor == null) {
                        //获取全部的数据
                        cursor = processFullOplog();
                    }
                    //遍历所有的数据,并处理
                    while (cursor.hasNext()) {
                        DBObject item = cursor.next();
                        // TokuMX secondaries can have ops in the oplog that
                        // have not yet been applied
                        // We need to wait until they have been applied before
                        // processing them
                        Object applied = item.get("a");
                        if (applied != null && !applied.equals(Boolean.TRUE)) {
                            log.debug("Encountered oplog entry with a:false, ts:" + item.get("ts"));
                            break;
                        }
                        //处理Entry
                        timestamp = processOplogEntry(item, timestamp);
                    }
                    //处理完成，休眠500ms
                    log.debug("Before waiting for 500 ms");
                    Thread.sleep(500);
                } finally {
                    if (cursor != null) {
                        log.trace("Closing oplog cursor");
                        cursor.close();
                    }
                }
            } catch (SlurperException e) {
                log.error("Exception in slurper", e);
                Thread.currentThread().interrupt();
                break;
            } catch (MongoInterruptedException | InterruptedException e) {
                log.info("river-mongodb slurper interrupted");
                Thread.currentThread().interrupt();
                break;
            } catch (MongoSocketException | MongoTimeoutException | MongoCursorNotFoundException e) {
                log.info("Oplog tailing - {} - {}. Will retry.", e.getClass().getSimpleName(), e.getMessage());
                log.debug("Total documents inserted so far by river {}: {}", definition.getRiverName(), totalDocuments.get());
                try {
                    Thread.sleep(MongoDBRiver.MONGODB_RETRY_ERROR_DELAY_MS);
                } catch (InterruptedException iEx) {
                    log.info("river-mongodb slurper interrupted");
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (Exception e) {
                log.error("Exception while looping in cursor", e);
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("Slurper is stopping. River has status {}", context.getStatus());
    }

    protected boolean riverHasIndexedFromOplog() {
        return MongoDBRiver.getLastTimestamp(esClient, definition) != null;
    }

    protected boolean isIndexEmpty() {
        return MongoDBRiver.getIndexCount(esClient, definition) == 0;
    }

    private Timestamp<?> getCurrentOplogTimestamp() {
        try (DBCursor cursor = oplogCollection.find().sort(new BasicDBObject(MongoDBRiver.INSERTION_ORDER_KEY, -1)).limit(1)) {
            return Timestamp.on(cursor.next());
        }
    }

    private DBCursor processFullOplog() throws InterruptedException, SlurperException {
        Timestamp<?> currentTimestamp = getCurrentOplogTimestamp();
        return oplogCursor(currentTimestamp);
    }

    private Timestamp<?> processOplogEntry(final DBObject entry, final Timestamp<?> startTimestamp) throws InterruptedException {
        // To support transactions, TokuMX wraps one or more operations in a
        // single oplog entry, in a list.
        // As long as clients are not transaction-aware, we can pretty safely
        // assume there will only be one operation in the list.
        // Supporting genuine multi-operation transactions will require a bit
        // more logic here.

        //这里暂时不支持事务
        flattenOps(entry);

        //判断是否是有效的entry
        if (!isValidOplogEntry(entry, startTimestamp)) {
            return startTimestamp;
        }

        //获取操作类型，并解析
        Operation operation = Operation.fromString(entry.get(MongoDBRiver.OPLOG_OPERATION).toString());
        String namespace = entry.get(MongoDBRiver.OPLOG_NAMESPACE).toString();
        String collection = null;
        Timestamp<?> oplogTimestamp = Timestamp.on(entry);
        DBObject object = (DBObject) entry.get(MongoDBRiver.OPLOG_OBJECT);

        if (definition.isImportAllCollections()) {
            if (namespace.startsWith(definition.getMongoDb()) && !namespace.equals(cmdOplogNamespace)) {
                collection = getCollectionFromNamespace(namespace);
            }
        } else {
            collection = definition.getMongoCollection();
        }

        if (namespace.equals(cmdOplogNamespace)) {
            if (object.containsField(MongoDBRiver.OPLOG_DROP_COMMAND_OPERATION)) {
                operation = Operation.DROP_COLLECTION;
                if (definition.isImportAllCollections()) {
                    collection = object.get(MongoDBRiver.OPLOG_DROP_COMMAND_OPERATION).toString();
                    if (collection.startsWith("tmp.mr.")) {
                        return startTimestamp;
                    }
                }
            }
            if (object.containsField(MongoDBRiver.OPLOG_DROP_DATABASE_COMMAND_OPERATION)) {
                operation = Operation.DROP_DATABASE;
            }
        }

        log.trace("namespace: {} - operation: {}", namespace, operation);
        if (namespace.equals(MongoDBRiver.OPLOG_ADMIN_COMMAND)) {
            if (operation == Operation.COMMAND) {
                processAdminCommandOplogEntry(entry, startTimestamp);
                return startTimestamp;
            }
        }

        if (log.isTraceEnabled()) {
            String deserialized = object.toString();
            if (deserialized.length() < 400) {
                log.trace("MongoDB object deserialized: {}", deserialized);
            } else {
                log.trace("MongoDB object deserialized is {} characters long", deserialized.length());
            }
            log.trace("collection: {}", collection);
            log.trace("oplog entry - namespace [{}], operation [{}]", namespace, operation);
            if (deserialized.length() < 400) {
                log.trace("oplog processing item {}", entry);
            }
        }

        String objectId = getObjectIdFromOplogEntry(entry);
        if (operation == Operation.DELETE) {
            // Include only _id in data, as vanilla MongoDB does, so
            // transformation scripts won't be broken by Toku
            if (object.containsField(MongoDBRiver.MONGODB_ID_FIELD)) {
                if (object.keySet().size() > 1) {
                    entry.put(MongoDBRiver.OPLOG_OBJECT, object = new BasicDBObject(MongoDBRiver.MONGODB_ID_FIELD, objectId));
                }
            } else {
                throw new NullPointerException(MongoDBRiver.MONGODB_ID_FIELD);
            }
        }

        if (definition.isMongoGridFS() && namespace.endsWith(MongoDBRiver.GRIDFS_FILES_SUFFIX)
                && (operation == Operation.INSERT || operation == Operation.UPDATE)) {
            if (objectId == null) {
                throw new NullPointerException(MongoDBRiver.MONGODB_ID_FIELD);
            }
            GridFS grid = new GridFS(mongoShardClient.getDB(definition.getMongoDb()), collection);
            GridFSDBFile file = grid.findOne(new ObjectId(objectId));
            if (file != null) {
                log.trace("Caught file: {} - {}", file.getId(), file.getFilename());
                object = file;
            } else {
                log.error("Cannot find file from id: {}", objectId);
            }
        }

        if (object instanceof GridFSDBFile) {
            if (objectId == null) {
                throw new NullPointerException(MongoDBRiver.MONGODB_ID_FIELD);
            }
            if (log.isTraceEnabled()) {
                log.trace("Add attachment: {}", objectId);
            }
            addToStream(operation, oplogTimestamp, applyFieldFilter(object), collection);
        } else {
            if (operation == Operation.UPDATE) {
                DBObject update = (DBObject) entry.get(MongoDBRiver.OPLOG_UPDATE);
                log.trace("Updated item: {}", update);
                addQueryToStream(operation, oplogTimestamp, update, collection);
            } else {
                if (operation == Operation.INSERT) {
                    addInsertToStream(oplogTimestamp, applyFieldFilter(object), collection);
                } else {
                    addToStream(operation, oplogTimestamp, applyFieldFilter(object), collection);
                }
            }
        }
        return oplogTimestamp;
    }

    //支持mongo 的 ref
    @SuppressWarnings("unchecked")
    private void flattenOps(DBObject entry) {
        Object ref = entry.removeField(MongoDBRiver.OPLOG_REF);
        Object ops = ref == null ? entry.removeField(MongoDBRiver.OPLOG_OPS) : getRefOps(ref);
        if (ops != null) {
            try {
                for (DBObject op : (List<DBObject>) ops) {
                    String operation = (String) op.get(MongoDBRiver.OPLOG_OPERATION);
                    if (operation.equals(MongoDBRiver.OPLOG_COMMAND_OPERATION)) {
                        DBObject object = (DBObject) op.get(MongoDBRiver.OPLOG_OBJECT);
                        if (object.containsField(MongoDBRiver.OPLOG_CREATE_COMMAND)) {
                            continue;
                        }
                    }
                    entry.putAll(op);
                }
            } catch (ClassCastException e) {
                log.error(e.toString(), e);
            }
        }
    }

    private Object getRefOps(Object ref) {
        // db.oplog.refs.find({_id: {$gte: {oid: %ref%}}}).limit(1)
        DBObject query = new BasicDBObject(MongoDBRiver.MONGODB_ID_FIELD, new BasicDBObject(QueryOperators.GTE,
                new BasicDBObject(MongoDBRiver.MONGODB_OID_FIELD, ref)));
        DBObject oplog = oplogRefsCollection.findOne(query);
        return oplog == null ? null : oplog.get("ops");
    }

    private void processAdminCommandOplogEntry(final DBObject entry, final Timestamp<?> startTimestamp) throws InterruptedException {
        if (log.isTraceEnabled()) {
            log.trace("processAdminCommandOplogEntry - [{}]", entry);
        }
        DBObject object = (DBObject) entry.get(MongoDBRiver.OPLOG_OBJECT);
        if (definition.isImportAllCollections()) {
            if (object.containsField(MongoDBRiver.OPLOG_RENAME_COLLECTION_COMMAND_OPERATION) && object.containsField(MongoDBRiver.OPLOG_TO)) {
                String to = object.get(MongoDBRiver.OPLOG_TO).toString();
                if (to.startsWith(definition.getMongoDb())) {
                    String newCollection = getCollectionFromNamespace(to);
                    DBCollection coll = slurpedDb.getCollection(newCollection);
                    CollectionSlurper importer = new CollectionSlurper(river, mongoClusterClient);
                    importer.importCollection(coll, timestamp);
                }
            }
        }
    }

    private String getCollectionFromNamespace(String namespace) {
        if (namespace.startsWith(definition.getMongoDb() + '.')) {
            return namespace.substring(definition.getMongoDb().length() + 1);
        }
        log.error("Cannot get collection from namespace [{}]", namespace);
        return null;
    }

    /**
     * 验证是否是有效的操作
     *
     * @param entry
     * @param startTimestamp
     * @return
     */
    private boolean isValidOplogEntry(final DBObject entry, final Timestamp<?> startTimestamp) {
        //是空操作
        if (!entry.containsField(MongoDBRiver.OPLOG_OPERATION)) {
            log.trace("[Empty Oplog Entry] - can be ignored. {}", JSONSerializers.getStrict().serialize(entry));
            return false;
        }
        //是 no-op操作
        if (MongoDBRiver.OPLOG_NOOP_OPERATION.equals(entry.get(MongoDBRiver.OPLOG_OPERATION))) {
            log.trace("[No-op Oplog Entry] - can be ignored. {}", JSONSerializers.getStrict().serialize(entry));
            return false;
        }
        String namespace = (String) entry.get(MongoDBRiver.OPLOG_NAMESPACE);
        // Initial support for sharded collection -
        // https://jira.mongodb.org/browse/SERVER-4333
        // Not interested in operation from migration or sharding
        //是不需要的操作
        if (entry.containsField(MongoDBRiver.OPLOG_FROM_MIGRATE) && ((BasicBSONObject) entry).getBoolean(MongoDBRiver.OPLOG_FROM_MIGRATE)) {
            log.trace("[Invalid Oplog Entry] - from migration or sharding operation. Can be ignored. {}", JSONSerializers.getStrict().serialize(entry));
            return false;
        }
        // Not interested by chunks - skip all
        //是chunk操作
        if (namespace.endsWith(MongoDBRiver.GRIDFS_CHUNKS_SUFFIX)) {
            return false;
        }

        //是这个时间点以前的操作
        if (startTimestamp != null) {
            Timestamp<?> oplogTimestamp = Timestamp.on(entry);
            if (Timestamp.compare(oplogTimestamp, startTimestamp) < 0) {
                log.error("[Invalid Oplog Entry] - entry timestamp [{}] before startTimestamp [{}]",
                        JSONSerializers.getStrict().serialize(entry), startTimestamp);
                return false;
            }
        }

        boolean validNamespace = false;
        if (definition.isMongoGridFS()) {
            //是否是gfs的命名空间
            validNamespace = gridfsOplogNamespace.equals(namespace);
        } else {
            //是否导入全部的表
            if (definition.isImportAllCollections()) {
                // Skip temp entry generated by map / reduce
                //跳过由map / reduce生成的临时entry
                if (namespace.startsWith(definition.getMongoDb()) && !namespace.startsWith(definition.getMongoDb() + ".tmp.mr")) {
                    validNamespace = true;
                }
            } else {
                //oplog也是有效的明明空间
                if (definition.getMongoOplogNamespace().equals(namespace)) {
                    validNamespace = true;
                }
            }
            if (cmdOplogNamespace.equals(namespace)) {
                validNamespace = true;
            }

            if (MongoDBRiver.OPLOG_ADMIN_COMMAND.equals(namespace)) {
                validNamespace = true;
            }
        }
        if (!validNamespace) {
            log.trace("[Invalid Oplog Entry] - namespace [{}] is not valid", namespace);
            return false;
        }
        String operation = (String) entry.get(MongoDBRiver.OPLOG_OPERATION);
        if (!oplogOperations.contains(operation)) {
            log.trace("[Invalid Oplog Entry] - operation [{}] is not valid", operation);
            return false;
        }

        // TODO: implement a better solution
        if (definition.getMongoOplogFilter() != null) {
            DBObject object = (DBObject) entry.get(MongoDBRiver.OPLOG_OBJECT);
            BasicDBObject filter = definition.getMongoOplogFilter();
            if (!filterMatch(filter, object)) {
                log.trace("[Invalid Oplog Entry] - filter [{}] does not match object [{}]", filter, object);
                return false;
            }
        }
        return true;
    }

    private boolean filterMatch(DBObject filter, DBObject object) {
        for (String key : filter.keySet()) {
            if (!object.containsField(key)) {
                return false;
            }
            if (!filter.get(key).equals(object.get(key))) {
                return false;
            }
        }
        return true;
    }

    private DBObject applyFieldFilter(DBObject object) {
        if (object instanceof GridFSFile) {
            GridFSFile file = (GridFSFile) object;
            DBObject metadata = file.getMetaData();
            if (metadata != null) {
                file.setMetaData(applyFieldFilter(metadata));
            }
        } else {
            object = MongoDBHelper.applyExcludeFields(object, definition.getExcludeFields());
            object = MongoDBHelper.applyIncludeFields(object, definition.getIncludeFields());
        }
        return object;
    }

    /*
     * Extract "_id" from "o" if it fails try to extract from "o2"
     */
    private String getObjectIdFromOplogEntry(DBObject entry) {
        if (entry.containsField(MongoDBRiver.OPLOG_OBJECT)) {
            DBObject object = (DBObject) entry.get(MongoDBRiver.OPLOG_OBJECT);
            if (object.containsField(MongoDBRiver.MONGODB_ID_FIELD)) {
                return object.get(MongoDBRiver.MONGODB_ID_FIELD).toString();
            }
        }
        if (entry.containsField(MongoDBRiver.OPLOG_UPDATE)) {
            DBObject object = (DBObject) entry.get(MongoDBRiver.OPLOG_UPDATE);
            if (object.containsField(MongoDBRiver.MONGODB_ID_FIELD)) {
                return object.get(MongoDBRiver.MONGODB_ID_FIELD).toString();
            }
        }
        return null;
    }

    private DBCursor oplogCursor(final Timestamp<?> time) throws SlurperException {
        //生成过滤条件
        DBObject indexFilter = time.getOplogFilter();
        if (indexFilter == null) {
            return null;
        }

        int options = Bytes.QUERYOPTION_TAILABLE | Bytes.QUERYOPTION_AWAITDATA | Bytes.QUERYOPTION_NOTIMEOUT
                // Using OPLOGREPLAY to improve performance:
                // https://jira.mongodb.org/browse/JAVA-771
                | Bytes.QUERYOPTION_OPLOGREPLAY;

        //查找所有符合条件的操作
        DBCursor cursor = oplogCollection.find(indexFilter).setOptions(options);
        Iterator<DBObject> iterator = cursor.iterator();

        // Toku sometimes gets stuck without this hint:
        if (indexFilter.containsField(MongoDBRiver.MONGODB_ID_FIELD)) {
            cursor = cursor.hint("_id_");
        }
        isRiverStale(cursor, time);
        return cursor;
    }

    //更新同步的时间点
    private void isRiverStale(DBCursor cursor, Timestamp<?> time) throws SlurperException {
        if (cursor == null || time == null) {
            return;
        }
        //初始化的同步时间
        if (definition.getInitialTimestamp() != null && time.equals(definition.getInitialTimestamp())) {
            return;
        }
        DBObject entry = cursor.next();
        Timestamp<?> oplogTimestamp = Timestamp.on(entry);
        if (!time.equals(oplogTimestamp)) {
            MongoDBRiverHelper.setRiverStatus(esClient, definition.getRiverName(), Status.RIVER_STALE);
            throw new SlurperException("River out of sync with oplog.rs collection");
        }
    }

    private void addQueryToStream(final Operation operation, final Timestamp<?> currentTimestamp, final DBObject update,
            final String collection) throws InterruptedException {
        if (log.isTraceEnabled()) {
            log.trace("addQueryToStream - operation [{}], currentTimestamp [{}], update [{}]", operation, currentTimestamp, update);
        }

        if (collection == null) {
            for (String name : slurpedDb.getCollectionNames()) {
                DBCollection slurpedCollection = slurpedDb.getCollection(name);
                addQueryToStream(operation, currentTimestamp, update, name, slurpedCollection);
            }
        } else {
            DBCollection slurpedCollection = slurpedDb.getCollection(collection);
            addQueryToStream(operation, currentTimestamp, update, collection, slurpedCollection);
        }
    }

    private void addQueryToStream(final Operation operation, final Timestamp<?> currentTimestamp, final DBObject update,
                final String collection, final DBCollection slurpedCollection) throws InterruptedException {
        try (DBCursor cursor = slurpedCollection.find(update, findKeys)) {
            for (DBObject item : cursor) {
                addToStream(operation, currentTimestamp, item, collection);
            }
        }
    }

    private String addInsertToStream(final Timestamp<?> currentTimestamp, final DBObject data, final String collection)
            throws InterruptedException {
        totalDocuments.incrementAndGet();
        addToStream(Operation.INSERT, currentTimestamp, data, collection);
        if (data == null) {
            return null;
        } else {
            return data.containsField(MongoDBRiver.MONGODB_ID_FIELD) ? data.get(MongoDBRiver.MONGODB_ID_FIELD).toString() : null;
        }
    }

    private void addToStream(final Operation operation, final Timestamp<?> currentTimestamp, final DBObject data, final String collection)
            throws InterruptedException {
        if (log.isTraceEnabled()) {
            String dataString = data.toString();
            if (dataString.length() > 400) {
                log.trace("addToStream - operation [{}], currentTimestamp [{}], data (_id:[{}], serialized length:{}), collection [{}]",
                        operation, currentTimestamp, data.get("_id"), dataString.length(), collection);
            } else {
                log.trace("addToStream - operation [{}], currentTimestamp [{}], data [{}], collection [{}]",
                        operation, currentTimestamp, dataString, collection);
            }
        }

        if (operation == Operation.DROP_DATABASE) {
            log.info("addToStream - Operation.DROP_DATABASE, currentTimestamp [{}], data [{}], collection [{}]",
                    currentTimestamp, data, collection);
            if (definition.isImportAllCollections()) {
                for (String name : slurpedDb.getCollectionNames()) {
                    log.info("addToStream - isImportAllCollections - Operation.DROP_DATABASE, currentTimestamp [{}], data [{}], collection [{}]",
                            currentTimestamp, data, name);
                    context.getStream().put(new MongoDBRiver.QueueEntry(currentTimestamp, Operation.DROP_COLLECTION, data, name));
                }
            } else {
                context.getStream().put(new MongoDBRiver.QueueEntry(currentTimestamp, Operation.DROP_COLLECTION, data, collection));
            }
        } else {
            context.getStream().put(new MongoDBRiver.QueueEntry(currentTimestamp, operation, data, collection));
        }
    }

}
