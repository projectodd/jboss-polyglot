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
    
    public void setService(MessageProcessorService service) {
        this.service = service;
    }
    
    public XASession getSession() {
        return this.service.getSession();
    }
    
    public MessageConsumer getConsumer() {
        return this.service.getConsumer();
    }
   
    
    private BaseMessageProcessorGroup group;
    private MessageProcessorService service;
}
