package org.projectodd.polyglot.messaging;

import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.XASession;

public abstract class BaseMessageProcessor implements MessageListener {

    public BaseMessageProcessorGroup getGroup() {
        return group;
    }
    public void setGroup(BaseMessageProcessorGroup group) {
        this.group = group;
    }
    public XASession getSession() {
        return session;
    }
    public void setSession(XASession session) {
        this.session = session;
    }
    public MessageConsumer getConsumer() {
        return consumer;
    }
    public void setConsumer(MessageConsumer consumer) {
        this.consumer = consumer;
    }
    
    private BaseMessageProcessorGroup group;
    private XASession session;
    private MessageConsumer consumer;

}
