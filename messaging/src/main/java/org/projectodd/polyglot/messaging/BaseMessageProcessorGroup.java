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

package org.projectodd.polyglot.messaging;

import java.lang.IllegalStateException;
import java.util.ArrayList;
import java.util.List;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.XAConnection;

import org.hornetq.jms.client.HornetQConnectionFactory;
import org.jboss.as.messaging.MessagingServices;
import org.jboss.as.messaging.jms.JMSServices;
import org.jboss.as.naming.ManagedReference;
import org.jboss.as.naming.ManagedReferenceFactory;
import org.jboss.logging.Logger;
import org.jboss.msc.inject.Injector;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

public class BaseMessageProcessorGroup implements Service<BaseMessageProcessorGroup> {

    public BaseMessageProcessorGroup(ServiceRegistry registry, ServiceName baseServiceName, String destinationName, Class<? extends BaseMessageProcessor> messageProcessorClass) {
        this.serviceRegistry = registry;
        this.baseServiceName = baseServiceName;
        this.destinationName = destinationName;
        this.messageProcessorClass = messageProcessorClass;
    }

    @Override
    public void start(final StartContext context) throws StartException {
    
        final boolean async = this.startAsynchronously;
        
        Runnable action = new Runnable() {
    
            @Override
            public void run() {
                ManagedReferenceFactory destinationManagedReferenceFactory = BaseMessageProcessorGroup.this.destinationInjector.getValue();
                ManagedReference destinationManagedReference = destinationManagedReferenceFactory.getReference();
                try {
                    BaseMessageProcessorGroup.this.destination = (Destination) destinationManagedReference.getInstance();
                } finally {
                    if (destinationManagedReference != null) {
                        destinationManagedReference.release();
                    }
                }
              
                startConnection( context );
                
                ServiceTarget target = context.getChildTarget();
    
                if (BaseMessageProcessorGroup.this.destination instanceof Queue) {
                    target.addDependency( JMSServices.getJmsQueueBaseServiceName( MessagingServices.getHornetQServiceName( "default" ) )
                            .append( BaseMessageProcessorGroup.this.destinationName ) );
                } else {
                    target.addDependency( JMSServices.getJmsTopicBaseServiceName( MessagingServices.getHornetQServiceName( "default" ) )
                            .append( BaseMessageProcessorGroup.this.destinationName ) );
                }

                try {
                    installMessageProcessors();
                } catch (Exception e) {
                    context.failed( new StartException( e ) );
                }

                BaseMessageProcessorGroup.this.running = true;
    
                if (async) {
                    context.complete();
                }
            }
    
        };
        
        if (async) {
            context.asynchronous();
            context.execute( action );
        } else {
            action.run();
        }
    
    }

    @Override
    public void stop(StopContext context) {
        try {
            this.connection.close();
        } catch (JMSException e) {
            log.error( "Error stopping consumer connection", e );
        }

    }

    @Override
    public BaseMessageProcessorGroup getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    public synchronized void start() throws Exception {
        if (this.running) {
            return;
        }

        installMessageProcessors();
        this.running = true;
    }

    public synchronized void stop() throws Exception {
        if (!this.running) {
            return;
        }

        for (BaseMessageProcessor processor : messageProcessors) {
            stopConsumer(processor);
        }
        this.running = false;
    }

    protected void installMessageProcessors() throws Exception {
        for (int i = 0; i < this.concurrency; ++i) {
            startConsumer(instantiateProcessor());
        }
    }

    protected void startConsumer(BaseMessageProcessor processor) throws Exception {
        Session session;
        MessageConsumer consumer;

        log.trace("Adding new consumer");

        if (isXAEnabled()) {
            session = getConnection().createXASession();
        } else {
            // Use local transactions for non-XA message processors
            session = getConnection().createSession( true, Session.SESSION_TRANSACTED );
        }

        Destination destination = getDestination();

        if (isDurable() && destination instanceof Topic) {
            consumer =  session.createDurableSubscriber( (Topic) destination, getName(), getMessageSelector(), false );
        } else {
            if (isDurable() && !(destination instanceof Topic)) {
                log.warn( "Durable set for processor " + getName() + ", but " + destination + " is not a topic - ignoring." );
            }
            consumer = session.createConsumer( destination, getMessageSelector());
        }

        processor.initialize(this, session, consumer);

        messageProcessors.add(processor);

        log.trace("Consumer added");
    }

    protected void stopConsumer(BaseMessageProcessor processor) throws Exception {
        processor.getConsumer().close();
        processor.getSession().close();
    }

    protected void startConnection(StartContext context) {
        
        ManagedReferenceFactory connectionFactoryManagedReferenceFactory = BaseMessageProcessorGroup.this.connectionFactoryInjector.getValue();
        ManagedReference connectionFactoryManagedReference = connectionFactoryManagedReferenceFactory.getReference();
        HornetQConnectionFactory connectionFactory = (HornetQConnectionFactory) connectionFactoryManagedReference.getInstance();

        try {
            this.connection = connectionFactory.createXAConnection();
            String clientID = this.clientID;
            if (this.durable && clientID != null) {
                String name = this.name;
                if (this.destination instanceof Topic) {
                    log.info( "Setting clientID for " + name + " to " + clientID );
                    this.connection.setClientID( clientID );
                } else {
                    log.warn( "ClientID set for processor " + name + ", but " + 
                            destination + " is not a topic - ignoring." );
                }
            } else if (this.durable && clientID == null) {
                context.failed( new StartException( "Durable topic processors require a client_id. processor: " + name ) );
            }
            this.connection.start();
        } catch (JMSException e) {
            context.failed( new StartException( e ) );
        } finally {
            if (connectionFactoryManagedReference != null) {
                connectionFactoryManagedReference.release();
            }
        }
 
    }
    
    protected BaseMessageProcessor instantiateProcessor() throws IllegalAccessException, InstantiationException {
        return this.messageProcessorClass.newInstance();
    }
    
    public String getDestinationName() {
        return this.destinationName;
    }

    public String getStatus() {
        if (this.running) {
            return "STARTED";
        }
        return "STOPPED";
    }

    public void updateConcurrency(int concurrency) throws Exception {
        if (this.concurrency == concurrency) {
            return;
        }

        if (concurrency < 1) {
            log.warn( "Cannot set concurrency of '" + getName() + "' message processor to < 1; requested: " + concurrency );
            return;
        }

        log.debug( "Changing '" + getName() + "' message processor concurrency (from " + this.concurrency + " to " + concurrency + ")" );

        if (messageProcessors.size() < concurrency) {
            log.trace( "Adding " + (concurrency - this.concurrency) + " new message processors" );

            while (messageProcessors.size() < concurrency) {
                // Create new processor
                BaseMessageProcessor processor = instantiateProcessor();
                // And start it
                startConsumer(processor);
            }
        } else {
            log.trace( "Removing " + (this.concurrency - concurrency) + " message processors" );

            while (messageProcessors.size() > concurrency) {
                // Get first processor
                BaseMessageProcessor processor = messageProcessors.get(0);
                // Stop the consumer and close the session
                stopConsumer(processor);
                // Remove the processor from the list
                messageProcessors.remove(processor);
            }
        }

        // Update the concurrency info after all
        setConcurrency(concurrency);
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }

    public int getConcurrency() {
        return this.concurrency;
    }

    public void setMessageSelector(String messageSelector) {
        this.messageSelector = messageSelector;
    }

    public String getMessageSelector() {
        return this.messageSelector;
    }

    public void setDurable(boolean durable) {
        this.durable = durable;
    }

    public boolean isDurable() {
        return this.durable;
    }

    public void setClientID(String clientID) {
        this.clientID = clientID;
    }

    public String getClientID() {
        return this.clientID;
    }

    public void setXAEnabled(boolean  xaEnabled) {
        this.xaEnabled = xaEnabled;
    }

    public boolean isXAEnabled() {
        return this.xaEnabled;
    }
    
    public Injector<ManagedReferenceFactory> getConnectionFactoryInjector() {
        return this.connectionFactoryInjector;
    }

    public Injector<ManagedReferenceFactory> getDestinationInjector() {
        return this.destinationInjector;
    }

    public XAConnection getConnection() {
        return this.connection;
    }

    protected void setConnection(XAConnection connection) {
        this.connection = connection;
    }
    
    public Destination getDestination() {
        return this.destination;
    }

    public ServiceName getBaseServiceName() {
        return baseServiceName;
    }

    protected ServiceRegistry getServiceRegistry() {
        return serviceRegistry;
    }

    protected void setStartAsynchronously(boolean startAsynchronously) {
        this.startAsynchronously = startAsynchronously;
    }

    private boolean startAsynchronously = true;
    private Class<? extends BaseMessageProcessor> messageProcessorClass;
    private XAConnection connection;
    private ServiceRegistry serviceRegistry;
    private String destinationName;
    private Destination destination;
    private ServiceName baseServiceName;
    private String name;
    private String messageSelector;
    private boolean durable;
    private String clientID;
    private boolean xaEnabled = true;
    private boolean running = false;
    private int concurrency;
    private List<BaseMessageProcessor> messageProcessors = new ArrayList<BaseMessageProcessor>();
    private final InjectedValue<ManagedReferenceFactory> connectionFactoryInjector = new InjectedValue<ManagedReferenceFactory>();
    private final InjectedValue<ManagedReferenceFactory> destinationInjector = new InjectedValue<ManagedReferenceFactory>();

    public static final Logger log = Logger.getLogger( "org.projectodd.polyglot.messaging" );
}
