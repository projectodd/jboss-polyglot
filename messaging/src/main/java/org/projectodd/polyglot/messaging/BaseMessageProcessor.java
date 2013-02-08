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

import java.lang.reflect.Field;

import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.transaction.TransactionManager;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.client.ClientConsumer;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.MessageHandler;
import org.hornetq.jms.client.HornetQMessage;
import org.hornetq.jms.client.HornetQMessageConsumer;
import org.hornetq.jms.client.HornetQSession;
import org.jboss.logging.Logger;
import org.jboss.msc.value.InjectedValue;

public abstract class BaseMessageProcessor implements MessageListener, MessageHandler {

    public void initialize(BaseMessageProcessorGroup group, Session session, MessageConsumer consumer) throws Exception {
        this.session = session;
        this.consumer = consumer;
        this.group = group;

        // Use HornetQ's Core API for message consumers where possible so we
        // get proper XA support. Otherwise, fall back to standard JMS.
        if (consumer instanceof HornetQMessageConsumer) {
            log.trace( "Using HornetQ Core API for Message Processor " + getGroup().getName() );
            Field sessionField = consumer.getClass().getDeclaredField( "session" );
            sessionField.setAccessible( true );
            this.hornetQSession = (HornetQSession) sessionField.get( consumer );

            Field consumerField = consumer.getClass().getDeclaredField( "consumer" );
            consumerField.setAccessible( true );
            this.clientConsumer = (ClientConsumer) consumerField.get( consumer );

            int ackMode = hornetQSession.getAcknowledgeMode();
            this.transactedOrClientAck = (ackMode == Session.SESSION_TRANSACTED || ackMode == Session.CLIENT_ACKNOWLEDGE) || hornetQSession.isXA();

            this.clientConsumer.setMessageHandler( this );
        } else {
            consumer.setMessageListener( this );
        }
    }

    public BaseMessageProcessorGroup getGroup() {
        return group;
    }
    
    public Session getSession() {
        return this.session;
    }
    
    public MessageConsumer getConsumer() {
        return this.consumer;
    }

    public boolean isXAEnabled() {
        return this.group.isXAEnabled();
    }

    protected HornetQSession getHornetQSession() {
        return this.hornetQSession;
    }

    protected ClientSession getCoreSession() {
        return getHornetQSession().getCoreSession();
    }

    protected TransactionManager getTransactionManager() {
        return this.transactionManagerInjector.getValue();
    }

    // No-op method that can be overridden in subclasses to do any work
    // necessary to prepare transactions
    protected void prepareTransaction() {}

    /**
     * This entire method is essentially a copy of HornetQ's
     * JMSMessageListenerWrapper onMessage but with hooks for preparing
     * transactions before calling MessageListener's onMessage
     * 
     */
    @Override
    public void onMessage(final ClientMessage message) {
        HornetQMessage msg = HornetQMessage.createMessage( message, getCoreSession() );
        log.trace( "MessageHandler.onMessage called for " + getGroup().getName() + " with messageId " + msg.getJMSMessageID() );

        try {
            msg.doBeforeReceive();
        } catch (Exception e) {
            log.error( "Failed to prepare message for receipt", e );
            return;
        }

        if (isXAEnabled()) {
            log.trace( "Preparing transaction for messageId " + msg.getJMSMessageID() );
            prepareTransaction();
        }

        if (transactedOrClientAck) {
           try {
              message.acknowledge();
              log.trace( "Acknowledging messageId " + msg.getJMSMessageID() + " before calling onMessage" );
           } catch (HornetQException e) {
              log.error( "Failed to process message", e );
           }
        }

        try {
            onMessage( msg );
        } catch (RuntimeException e) {
            log.warn( "Unhandled exception thrown from onMessage", e );

            if (!transactedOrClientAck) {
                try {
                    log.trace( "Rolling back messageId " + msg.getJMSMessageID() );
                    getCoreSession().rollback( true );
                    getHornetQSession().setRecoverCalled( true );
                } catch (Exception e2) {
                    log.error( "Failed to recover session", e2 );
                }
            }
         }

        if (!getHornetQSession().isRecoverCalled()) {
           try {
              // We don't want to call this if the consumer was closed from inside onMessage
              if (!clientConsumer.isClosed() && !transactedOrClientAck) {
                  log.trace( "Acknowledging messageId " + msg.getJMSMessageID() + " after calling onMessage" );
                 message.acknowledge();
              }
           } catch (Exception e) {
              log.error( "Failed to process message", e );
           }
        }

        getHornetQSession().setRecoverCalled( false );
    }
   
    
    private BaseMessageProcessorGroup group;
    private HornetQSession hornetQSession;
    private ClientConsumer clientConsumer;
    private Session session;
    private MessageConsumer consumer;
    private InjectedValue<TransactionManager> transactionManagerInjector = new InjectedValue<TransactionManager>();
    private boolean transactedOrClientAck;
    private final Logger log = Logger.getLogger( this.getClass() );
}
