/*
 * Copyright 2008-2013 Red Hat, Inc, and individual contributors.
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

package org.projectodd.polyglot.messaging.destinations;

import org.jboss.as.messaging.jms.JMSTopicService;
import org.jboss.logging.Logger;
import org.jboss.msc.inject.InjectionException;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.StopContext;

import java.util.concurrent.ExecutorService;

public class DestroyableJMSTopicService extends JMSTopicService implements
        DestinationService, Destroyable, Injector<ExecutorService> {

    public DestroyableJMSTopicService(String topicName, String[] jndi) {
        super(topicName, jndi);
        this.topicName = topicName;
        //store this so we can check for reconfiguration. Not actually used for
        //configuration - the values passed to super() are
        this.jndi = jndi;
    }

    @Override
    public synchronized void stop(StopContext context) {
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
        
        super.stop( context );
    }

    @Override
    public String toString() {
        return "<DestroyableJMSTopicService@" + hashCode() + " " + this.topicName + ">";
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

    public String[] getJndi() {
        return this.jndi;
    }

    private String topicName;
    private boolean shouldDestroy = false;
    private String[] jndi;

    static final Logger log = Logger.getLogger( "org.projectodd.polyglot.messaging" );
}
