package org.projectodd.polyglot.messaging;

import java.util.ArrayList;
import java.util.List;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.XAConnection;

import org.hornetq.jms.client.HornetQConnectionFactory;
import org.jboss.as.messaging.MessagingServices;
import org.jboss.as.messaging.jms.JMSServices;
import org.jboss.as.naming.ManagedReference;
import org.jboss.as.naming.ManagedReferenceFactory;
import org.jboss.logging.Logger;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
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
    
    public void start(final StartContext context) throws StartException {
    
        context.asynchronous();
        context.execute( new Runnable() {
    
            @Override
            public void run() {
                ManagedReferenceFactory connectionFactoryManagedReferenceFactory = BaseMessageProcessorGroup.this.connectionFactoryInjector.getValue();
                ManagedReference connectionFactoryManagedReference = connectionFactoryManagedReferenceFactory.getReference();
                HornetQConnectionFactory connectionFactory = (HornetQConnectionFactory) connectionFactoryManagedReference.getInstance();
    
                try {
                    BaseMessageProcessorGroup.this.connection = connectionFactory.createXAConnection();
                    BaseMessageProcessorGroup.this.connection.start();
                } catch (JMSException e) {
                    context.failed( new StartException( e ) );
                } finally {
                    if (connectionFactoryManagedReference != null) {
                        connectionFactoryManagedReference.release();
                    }
                }
    
                ManagedReferenceFactory destinationManagedReferenceFactory = BaseMessageProcessorGroup.this.destinationInjector.getValue();
                ManagedReference destinationManagedReference = destinationManagedReferenceFactory.getReference();
                try {
                    BaseMessageProcessorGroup.this.destination = (Destination) destinationManagedReference.getInstance();
                } finally {
                    if (destinationManagedReference != null) {
                        destinationManagedReference.release();
                    }
                }
    
                ServiceTarget target = context.getChildTarget();
    
                if (BaseMessageProcessorGroup.this.destination instanceof Queue) {
                    //target.addDependency( JMSServices.JMS_QUEUE_BASE.append( BaseMessageProcessorGroup.this.destinationName ) );
                    target.addDependency( JMSServices.getJmsQueueBaseServiceName( MessagingServices.getHornetQServiceName( "default" ) ).append( BaseMessageProcessorGroup.this.destinationName ) );
                } else {
                    //target.addDependency( JMSServices.JMS_TOPIC_BASE.append( BaseMessageProcessorGroup.this.destinationName ) );
                    target.addDependency( JMSServices.getJmsTopicBaseServiceName( MessagingServices.getHornetQServiceName( "default" ) ).append( BaseMessageProcessorGroup.this.destinationName ) );
                }
    
                for (int i = 0; i < BaseMessageProcessorGroup.this.concurrency; ++i) {
                    BaseMessageProcessor processor = null;
                    try {
                        processor = messageProcessorClass.newInstance();
                    } catch (IllegalAccessException e) {
                        context.failed( new StartException( e ) );
                    } catch (InstantiationException e) {
                        context.failed( new StartException( e ) );
                    }
                    
                    MessageProcessorService service = new MessageProcessorService( BaseMessageProcessorGroup.this, processor );
                    ServiceName serviceName = baseServiceName.append( "" + i );
                    target.addService( serviceName, service )
                            .install();
                    services.add( serviceName );
                }
    
                BaseMessageProcessorGroup.this.running = true;
    
                context.complete();
    
            }
    
        } );
    
    }
    
    public synchronized void stop() throws Exception {
        for (ServiceName eachName : this.services) {
            ServiceController<?> each = this.serviceRegistry.getService( eachName );
            each.setMode( Mode.NEVER );
        }
        this.running = false;
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

    @Override
    public BaseMessageProcessorGroup getValue() throws IllegalStateException, IllegalArgumentException {
        return this;    
    }

    public synchronized void start() throws Exception {
        for (ServiceName eachName : this.services) {
            ServiceController<?> each = this.serviceRegistry.getService( eachName );
            each.setMode( Mode.ACTIVE );
        }
        this.running = true;
    }

    @Override
    public void stop(StopContext context) {
        try {
            this.connection.close();
        } catch (JMSException e) {
            log.error( "Error stopping consumer connection", e );
        }
    
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

    public Injector<ManagedReferenceFactory> getConnectionFactoryInjector() {
        return this.connectionFactoryInjector;
    }

    public Injector<ManagedReferenceFactory> getDestinationInjector() {
        return this.destinationInjector;
    }

    public XAConnection getConnection() {
        return this.connection;
    }

    public Destination getDestination() {
        return this.destination;
    }

    private Class<? extends BaseMessageProcessor> messageProcessorClass;
    private ServiceRegistry serviceRegistry;
    private String destinationName;
    private XAConnection connection;
    private Destination destination;
    private ServiceName baseServiceName;
    private String name;
    private String messageSelector;
    private boolean durable;
    private boolean running = false;
    private int concurrency;
    private List<ServiceName> services = new ArrayList<ServiceName>();
    private final InjectedValue<ManagedReferenceFactory> connectionFactoryInjector = new InjectedValue<ManagedReferenceFactory>();
    private final InjectedValue<ManagedReferenceFactory> destinationInjector = new InjectedValue<ManagedReferenceFactory>();

    public static final Logger log = Logger.getLogger( "org.projectodd.polyglot.messaging" );
}
