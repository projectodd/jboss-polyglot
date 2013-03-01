package org.projectodd.polyglot.stomp;

import org.jboss.as.network.SocketBinding;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.value.InjectedValue;
import org.projectodd.stilts.stomp.server.Connector;
import org.projectodd.stilts.stomp.server.StompServer;
import org.projectodd.stilts.stomplet.server.StompletServer;

public abstract class AbstractStompConnectorService implements Service<Connector>{

    public AbstractStompConnectorService() {
    }
    
    @Override
    public Connector getValue() throws IllegalStateException, IllegalArgumentException {
        return this.connector;
    }

    public Injector<SocketBinding> getSocketBindingInjector() {
        return this.socketBindingInjector;
    }
    
    public Injector<StompletServer> getStompletServerInjector() {
        return this.stompletServerInjector;
    }
    
    protected Connector connector;
    protected InjectedValue<SocketBinding> socketBindingInjector = new InjectedValue<SocketBinding>();
    protected InjectedValue<StompletServer> stompletServerInjector = new InjectedValue<StompletServer>();

}
