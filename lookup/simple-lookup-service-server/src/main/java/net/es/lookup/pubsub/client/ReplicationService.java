package net.es.lookup.pubsub.client;

import net.es.lookup.client.QueryClient;
import net.es.lookup.client.SimpleLS;
import net.es.lookup.client.Subscriber;
import net.es.lookup.client.SubscriberListener;
import net.es.lookup.common.Message;
import net.es.lookup.common.ReservedValues;
import net.es.lookup.common.exception.LSClientException;
import net.es.lookup.common.exception.ParserException;
import net.es.lookup.common.exception.QueryException;
import net.es.lookup.common.exception.internal.ConfigurationException;
import net.es.lookup.common.exception.internal.DatabaseException;
import net.es.lookup.common.exception.internal.DuplicateEntryException;
import net.es.lookup.database.DBMapping;
import net.es.lookup.database.ServiceDAOMongoDb;
import net.es.lookup.queries.Query;
import net.es.lookup.records.Record;
import net.es.lookup.utils.config.data.Cache;
import net.es.lookup.utils.config.data.SubscriberSource;
import net.es.lookup.utils.config.reader.SubscriberConfigReader;
import org.apache.log4j.Logger;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Author: sowmya
 * Date: 4/25/13
 * Time: 6:01 PM
 */
public class ReplicationService implements SubscriberListener {

    private List<SimpleLS> servers;
    private List<List<Map<String, Object>>> queries;
    private List<Subscriber> subscribers;
    private ServiceDAOMongoDb db;
    SubscriberConfigReader subscriberConfigReadercfg;
    private static Logger LOG = Logger.getLogger(ReplicationService.class);

    public ReplicationService(Cache cache) throws LSClientException, ConfigurationException {
        if(cache==null){
            System.out.println("Fatal error while creating ArchiveService. Exiting");
            System.exit(0);
        }
        db = DBMapping.getDb(cache.getName());

        subscriberConfigReadercfg = SubscriberConfigReader.getInstance();
        servers = new ArrayList<SimpleLS>();
        queries = new ArrayList<List<Map<String, Object>>>();
        subscribers = new ArrayList<Subscriber>();
        List<SubscriberSource> subscriberSourceList = cache.getSources();
        int count = subscriberSourceList.size();
        LOG.info("net.es.lookup.pubsub.client.ArchiveService: Initializing " + count + " hosts");
        List<Map<String, Object>> serverQueries = new LinkedList<Map<String, Object>>();
        for (int i = 0; i < count; i++) {
            try {
                SubscriberSource subscriberSource = subscriberSourceList.get(i);
                String url = subscriberSource.getAccessPoint();
                URI uri = new URI(url);
                String host = uri.getHost();
                int port = uri.getPort();
                List<Query> queryList = subscriberSource.getQueries();


                for (Query query: queryList){
                    Map<String, Object> queryMap = query.getMap();
                    serverQueries.add(queryMap);
                }
                queries.add(i, serverQueries);
                SimpleLS server = new SimpleLS(host, port);
                servers.add(server);

            } catch (URISyntaxException e) {
                LOG.error("net.es.lookup.pubsub.client.ArchiveService: Initializing " + count + " hosts");
                throw new LSClientException("net.es.lookup.pubsub.client.ArchiveService: Error initializing subscribe hosts -" + e.getMessage());}

        }

        init();
    }

    private void init() throws LSClientException {

        for (SimpleLS server : servers) {
            server.connect();
        }


    }

    public void start() throws LSClientException {
        LOG.info("net.es.lookup.pubsub.client.ReplicationService.start: Creating and starting the subscriber connections");
        int index = 0;
        for (SimpleLS server : servers) {
            List<Map<String, Object>> queryList = null;

            queryList = queries.get(index);
            SimpleLS queryserver =  new SimpleLS(server.getHost(),server.getPort());
            index++;


            if (queryList != null && !queryList.isEmpty()) {
                for (int i = 0; i < queryList.size(); i++) {
                    Map<String, Object> m = queryList.get(i);
                    if (m != null) {
                        try {
                            Query query = new Query(m);

                            //get the initial set of records before starting subscribe
                            try {
                                getRecords(query, queryserver);
                            } catch (ParserException e) {
                                LOG.error("net.es.lookup.pubsub.client.ReplicationService.start: Error parsing query results - " + e.getMessage());
                            } catch (QueryException e) {
                                LOG.error("net.es.lookup.pubsub.client.ReplicationService.start: Error processing query - " + e.getMessage());
                            }
                            Subscriber subscriber = new Subscriber(server, query);
                            subscriber.addListener(this);
                            subscriber.startSubscription();
                            subscribers.add(subscriber);
                        } catch (QueryException e) {
                            LOG.error("net.es.lookup.pubsub.client.ReplicationService.start: Error defining query");
                            throw new LSClientException("Query could not be defined" + e.getMessage());
                        }
                    }

                }


            } else {
                //if no list exists, create empty query
                Query query = new Query();


                //get the initial set of records before starting subscribe
                try {
                    getRecords(query, queryserver);
                } catch (ParserException e) {
                    LOG.error("net.es.lookup.pubsub.client.ReplicationService.start: Error parsing query results - " + e.getMessage());
                } catch (QueryException e) {
                    LOG.error("net.es.lookup.pubsub.client.ReplicationService.start: Error processing query - " + e.getMessage());
                }
                Subscriber subscriber = new Subscriber(server, query);
                subscriber.addListener(this);
                subscriber.startSubscription();
                subscribers.add(subscriber);

            }



        }

        LOG.info("net.es.lookup.pubsub.client.ReplicationService.start: Created and initialized " + subscribers.size() + " subscriber connections");


    }

    public void stop() throws LSClientException {

        LOG.info("net.es.lookup.pubsub.client.ReplicationService.stop: Stopping "+ subscribers.size() +" subscriber connections");
        for (Subscriber subscriber : subscribers) {
            subscriber.removeListener(this);
            subscriber.stopSubscription();
        }
        LOG.info("net.es.lookup.pubsub.client.ReplicationService.stop: Stopped "+ subscribers.size() +" subscriber connections");

    }


    public void onRecord(Record record) {
        LOG.info("net.es.lookup.pubsub.client.ReplicationService.onRecord: Processing Received message");
        try {
            save(record);
        } catch (DuplicateEntryException e) {
            LOG.error("net.es.lookup.pubsub.client.ReplcationService.onRecord: Error saving record" + e.getMessage());
        } catch (DatabaseException e) {
            LOG.error("net.es.lookup.pubsub.client.ReplicationService.onRecord: Error saving record" + e.getMessage());
        }
    }

    /**
     * The records' record-state will be updated.
     * If unable to update, then an exception will be thrown.
     * No records are deleted
     *
     * @param record The record to be save or updated
     */
    private void save(Record record) throws DuplicateEntryException, DatabaseException{
        LOG.info("net.es.lookup.pubsub.client.ReplicationService.onRecord: Processing Received message");
        if (record.getRecordState().equals(ReservedValues.RECORD_VALUE_STATE_REGISTER)) {
            LOG.info("net.es.lookup.pubsub.client.ReplicationService.onRecord: insert as new record");
            Message message = new Message(record.getMap());
            Map<String, Object> keyValues = record.getMap();
            Message operators = new Message();
            Message query = new Message();

            Iterator it = keyValues.entrySet().iterator();
            LOG.info("net.es.lookup.pubsub.client.ReplicationService.onRecord: Constructing query based on message");
            while (it.hasNext()) {

                Map.Entry<String, Object> pairs = (Map.Entry) it.next();
                operators.add(pairs.getKey(), ReservedValues.RECORD_OPERATOR_ALL);
                query.add(pairs.getKey(), pairs.getValue());

            }
            LOG.info("net.es.lookup.pubsub.client.ReplicationService.onRecord: Check and insert record");
            try {
                db.queryAndPublishService(message, query, operators);
            } catch (DatabaseException e) {
                LOG.error("net.es.lookup.pubsub.client.ReplicationService.onRecord: Error inserting record. Database Error"+ e.getMessage());
            } catch (DuplicateEntryException e) {
                LOG.error("net.es.lookup.pubsub.client.ReplicationService.onRecord: Error inserting record. Duplicate Exception Error"+ e.getMessage());
            }

            LOG.info("net.es.lookup.pubsub.client.ReplicationService.onRecord: Inserted record");

        }else if(record.getRecordState().equals(ReservedValues.RECORD_VALUE_STATE_RENEW)){
            String recordUri = record.getURI();
            Message message = new Message(record.getMap());
            LOG.info("net.es.lookup.pubsub.client.ReplicationService.onRecord: renew existing record");
            try {
                db.updateService(recordUri, message);
            } catch (DatabaseException e) {
                LOG.error("net.es.lookup.pubsub.client.ReplicationService.onRecord: Error renewing record. Database Error"+ e.getMessage());
            }
        }else if(record.getRecordState().equals(ReservedValues.RECORD_VALUE_STATE_EXPIRE)){
            String recordUri = record.getURI();
            Message message = new Message(record.getMap());
            LOG.info("net.es.lookup.pubsub.client.ReplicationService.onRecord: received expired record");
            try {
                db.updateService(recordUri, message);
            } catch (DatabaseException e) {
                LOG.error("net.es.lookup.pubsub.client.ReplicationService.onRecord: Error expiring record. Database Error"+ e.getMessage());
            }
        }else if(record.getRecordState().equals(ReservedValues.RECORD_VALUE_STATE_DELETE)){
            String recordUri = record.getURI();
            Message message = new Message(record.getMap());
            LOG.info("net.es.lookup.pubsub.client.ReplicationService.onRecord: received deleted record");
            try {
                db.deleteService(recordUri);
            } catch (DatabaseException e) {
                LOG.error("net.es.lookup.pubsub.client.ReplicationService.onRecord: Error deleting record. Database Error"+ e.getMessage());
            }
        }
    }


    /**
     * This method will only create a new entry for the record.
     * If an entry already exists, the insert operation is ignored and method returns.
     *
     * @param record The record to be save or updated
     */
    private void forceSave(Record record) throws DuplicateEntryException, DatabaseException {

        LOG.info("net.es.lookup.pubsub.client.ReplicationService.forceSave: insert as new record");
        Message message = new Message(record.getMap());
        Map<String, Object> keyValues = record.getMap();
        Message operators = new Message();
        Message query = new Message();

        Iterator it = keyValues.entrySet().iterator();
        LOG.info("net.es.lookup.pubsub.client.ReplicationService.forceSave: Constructing query based on message");
        while (it.hasNext()) {

            Map.Entry<String, Object> pairs = (Map.Entry) it.next();
            operators.add(pairs.getKey(), ReservedValues.RECORD_OPERATOR_ALL);
            query.add(pairs.getKey(), pairs.getValue());

        }
        LOG.info("net.es.lookup.pubsub.client.ReplicationService.forceSave: Check and insert record");

        db.queryAndPublishService(message, query, operators);

        LOG.info("net.es.lookup.pubsub.client.ReplicationService.forceSave: Inserted record");
    }


    private void getRecords(Query query, SimpleLS server) throws LSClientException, QueryException, ParserException {


        QueryClient queryClient = new QueryClient(server);
        queryClient.setQuery(query);
        List<Record> results = queryClient.query();
        for (Record record : results) {
            try {
                forceSave(record);
            } catch (DuplicateEntryException e) {
                LOG.error("net.es.lookup.pubsub.client.ReplicationService.getRecords: Error inserting record to DB - " + e.getMessage());
            } catch (DatabaseException e) {
                LOG.error("net.es.lookup.pubsub.client.ReplicationService.getRecords: Error inserting record to DB - " + e.getMessage());
            }
        }


    }

}
