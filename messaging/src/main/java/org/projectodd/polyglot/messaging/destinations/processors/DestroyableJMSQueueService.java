package org.projectodd.polyglot.messaging.destinations.processors;

import org.jboss.as.messaging.jms.JMSQueueService;
import org.jboss.msc.service.StopContext;

public class DestroyableJMSQueueService extends JMSQueueService {


    public DestroyableJMSQueueService(String queueName, String selectorString, boolean durable, String[] jndi) {
        super( queueName, selectorString, durable, jndi );
        this.shouldDestroy = ! durable;
        this.queueName = queueName;
    }

    @Override
    public synchronized void stop(StopContext context) {
        super.stop( context );
        
        if ( shouldDestroy ) {
            try {
                getJmsServer().getValue().destroyQueue( this.queueName );
            } catch (IllegalStateException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    private boolean shouldDestroy;
    private String queueName;



}
