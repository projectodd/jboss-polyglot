package org.projectodd.polyglot.messaging.destinations.processors;

import java.util.concurrent.ExecutorService;

import org.jboss.msc.inject.InjectionException;
import org.jboss.msc.inject.Injector;

public class JMSTopicService extends org.jboss.as.messaging.jms.JMSTopicService implements Injector<ExecutorService> {

    public JMSTopicService(String topicName, String[] jndi) {
        super(topicName, jndi);
    }

    /**
     * In EAP JMSTopicService getExecutorInjector() returns an
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

}
