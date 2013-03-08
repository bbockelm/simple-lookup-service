package net.es.lookup.pubsub;

import net.es.lookup.common.Message;
import net.es.lookup.common.exception.internal.QueryException;
import net.es.lookup.common.exception.internal.QueueException;

import java.util.List;

/**
 * This defines the interface that manages the pub-sub queues
 * for the lookup service.
 *
 * Author: sowmya
 * Date: 2/25/13
 * Time: 2:50 PM
 */
public interface QueueManager {

    /**
     * This method is used to retrieve the list of queues that
     * are assigned for a particular query. It creates one if none is found.
     *
     * @param query The lookup service query for which a queue is required
     * @return Returns a listof queue identifiers (string)
     */
    public List<String> getQueues(Message query) throws QueryException, QueueException;


    /**
     * This method pushes data to queue
     *
     * @param qid     The queue id to which data needs to be pushed to
     * @param message The data to be pushed
     *
     * @return void
     */
    public void push(String qid, Message message) throws QueueException;

}
