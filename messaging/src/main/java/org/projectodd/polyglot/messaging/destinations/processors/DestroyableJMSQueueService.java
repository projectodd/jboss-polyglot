/*
 * Copyright 2008-2012 Red Hat, Inc, and individual contributors.
 * 
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 * 
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.projectodd.polyglot.messaging.destinations.processors;

import java.util.concurrent.ExecutorService;

import org.jboss.as.messaging.jms.JMSQueueService;
import org.jboss.logging.Logger;
import org.jboss.msc.inject.InjectionException;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StopContext;

public class DestroyableJMSQueueService extends JMSQueueService implements Destroyable, Injector<ExecutorService> {


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
            } catch (Exception e) {
                if (null != e.getCause()) {
                    log.warn(e.getCause().getMessage());
                } else {
                    log.error("Can't destroy queue", e);
                }
            }
        }
    }

    /**
     * In EAP JMSQueueService getExecutorInjector() returns an
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

    private boolean shouldDestroy;
    private String queueName;

    static final Logger log = Logger.getLogger( "org.projectodd.polyglot.messaging" );

}
