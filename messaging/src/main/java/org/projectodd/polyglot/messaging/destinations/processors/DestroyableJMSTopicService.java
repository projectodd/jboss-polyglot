package org.projectodd.polyglot.messaging.destinations.processors;

import java.util.concurrent.ExecutorService;

import org.jboss.logging.Logger;
import org.jboss.msc.inject.InjectionException;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.StopContext;

public class DestroyableJMSTopicService extends org.jboss.as.messaging.jms.JMSTopicService implements Destroyable, Injector<ExecutorService> {

    public DestroyableJMSTopicService(String topicName, String[] jndi) {
        super(topicName, jndi);
        this.topicName = topicName;
    }

    @Override
    public synchronized void stop(StopContext context) {
        super.stop( context );
        
        if ( this.shouldDestroy ) {
            try {
                getJmsServer().getValue().destroyTopic( this.topicName );
            } catch (Exception e) {
                if (null != e.getCause()) {
                    log.warn(e.getCause().getMessage());
                } else {
                    log.error("Can't destroy topic", e);
                }
            }
        }
    }
    
    /**
     * In EAP DestroyableJMSTopicService getExecutorInjector() returns an
     * Injector<Executor> but in AS7 it returns Injector<ExecutorService>.
     * 
     * So, by implementing the Injector<ExecutorService> ourself we can ensure
     * that getExecutorInjector returns an Injector<ExecutorService> on both.
     * 
     */
    public Injector<ExecutorService> getExecutorServiceInjector() {
        return this;
    }

    public void inject(ExecutorService value) throws InjectionException {
        super.getExecutorInjector().inject( value );
    }

    public void uninject() {
        super.getExecutorInjector().uninject();
    }

    @Override
    public boolean willDestroy() {
        return shouldDestroy;
    }

    @Override
    public void setShouldDestroy(boolean shouldDestroy) {
        this.shouldDestroy = shouldDestroy;
    }

    private String topicName;
    private boolean shouldDestroy = false;
    
    static final Logger log = Logger.getLogger( "org.projectodd.polyglot.messaging" );
}
