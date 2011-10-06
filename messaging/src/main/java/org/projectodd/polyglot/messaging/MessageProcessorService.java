/*
 * Copyright 2008-2011 Red Hat, Inc, and individual contributors.
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

package org.projectodd.polyglot.messaging;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Topic;
import javax.jms.XASession;

import org.jboss.logging.Logger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

public class MessageProcessorService implements Service<Void> {

    MessageProcessorService(BaseMessageProcessorGroup group, BaseMessageProcessor listener) {
        this.group = group;
        this.listener = listener;
    }

    @Override
    public void start(final StartContext context) throws StartException {

        context.asynchronous();
        context.execute( new Runnable() {

            @Override
            public void run() {
                try {
                    session = group.getConnection().createXASession();
                    Destination destination = group.getDestination();
                    if (group.isDurable() && destination instanceof Topic) {
                        consumer = session.createDurableSubscriber( (Topic) destination, group.getName(), group.getMessageSelector(), false );
                    } else {
                        if (group.isDurable() && !(destination instanceof Topic)) {
                            log.warn( "Durable set for processor " + group.getName() + ", but " + destination + " is not a topic - ignoring." );
                        }
                        consumer = session.createConsumer( destination, group.getMessageSelector() );
                    }
                    listener.setService( MessageProcessorService.this );
                    listener.setGroup( group );
                    
                    consumer.setMessageListener( listener );

                    context.complete();
                } catch (JMSException e) {
                    context.failed( new StartException( e ) );
                }
            }
        } );

    }

   

    @Override
    public void stop(StopContext context) {
        try {
            this.consumer.close();
            this.consumer = null;
        } catch (JMSException e) {
            log.error( "Error closing consumer connection", e );
        }
        try {
            this.session.close();
        } catch (JMSException e) {
            log.error( "Error closing consumer session", e );
        }
    }

    @Override
    public Void getValue() throws IllegalStateException, IllegalArgumentException {
        return null;
    }

    
    // -------

    public XASession getSession() {
        return this.session;
    }

    public MessageConsumer getConsumer() {
        return this.consumer;
    }


    public static final Logger log = Logger.getLogger( "org.projectodd.polyglot.messaging" );

    private BaseMessageProcessorGroup group;
    private BaseMessageProcessor listener;
    private XASession session;
    private MessageConsumer consumer;
}
