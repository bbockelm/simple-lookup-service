package net.es.lookup.pubsub.client.heartbeat;

import net.es.lookup.client.Subscriber;
import net.es.lookup.common.exception.LSClientException;
import net.es.lookup.pubsub.client.Cache;
import net.es.lookup.records.PubSub.SubscribeRecord;
import net.es.lookup.service.CacheService;
import org.apache.log4j.Logger;
import org.joda.time.Instant;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.List;

/**
 * Author: sowmya
 * Date: 3/21/14
 * Time: 4:03 PM
 */
public class CacheHeartBeat implements Job {

    private static Logger LOG = Logger.getLogger(CacheHeartBeat.class);

    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {

        CacheService cacheService = CacheService.getInstance();
        List<Cache> cacheList = cacheService.getCacheList();
        LOG.debug("net.es.lookup.service.CacheService: Cache Heartbeat");
        boolean restartRequired = false;
        for (Cache cache : cacheList) {
            List<Subscriber> subscribers = cache.getSubscribers();
            for(Subscriber subscriber: subscribers){
                try {
                    SubscribeRecord record = subscriber.heartbeat();
                    Instant queueCreationTime = record.getQueueCreationTime();
                    Instant cacheRestart = cache.getLastRestartedTimeStamp();
                    //System.out.println("Is "+cacheRestart.toString()+" after "+queueCreationTime.toString()+"?");
                    if(cacheRestart.isBefore(queueCreationTime.plus(120000))){
                        cache.restart();

                        break;
                    }else{
                        LOG.error("No restart required");
                    }
                } catch (LSClientException e) {
                    LOG.error("Heartbeat message Failed"+ e.getMessage());
                }
            }
        }
        }
    }

