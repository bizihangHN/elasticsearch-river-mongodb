package org.elasticsearch.river.mongodb;

import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.river.mongodb.util.MongoDBRiverHelper;

@Slf4j
class StatusChecker implements Runnable {

    private final MongoDBRiver mongoDBRiver;
    private final MongoDBRiverDefinition definition;
    private final SharedContext context;

    public StatusChecker(MongoDBRiver mongoDBRiver, MongoDBRiverDefinition definition, SharedContext context) {
        this.mongoDBRiver = mongoDBRiver;
        this.definition = definition;
        this.context = context;
    }

    @Override
    public void run() {
        while (true) {
            try {
                Status status = MongoDBRiverHelper.getRiverStatus(this.mongoDBRiver.esClient, this.definition.getRiverName());
                if (status != this.context.getStatus()) {
                    if (status == Status.RUNNING && this.context.getStatus() != Status.STARTING) {
                        log.trace("About to start river: {}", this.definition.getRiverName());
                        mongoDBRiver.internalStartRiver();
                    } else if (status == Status.STOPPED) {
                        log.info("About to stop river: {}", this.definition.getRiverName());
                        mongoDBRiver.internalStopRiver();
                    }
                }
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                log.debug("Status thread interrupted", e, (Object) null);
                Thread.currentThread().interrupt();
                break;
            }

        }
    }
}
