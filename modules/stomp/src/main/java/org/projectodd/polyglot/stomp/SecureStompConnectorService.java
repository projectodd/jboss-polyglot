package org.projectodd.polyglot.stomp;

import javax.net.ssl.SSLContext;

import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.projectodd.stilts.stomp.server.SecureConnector;

public class SecureStompConnectorService extends AbstractStompConnectorService {

    @Override
    public void start(StartContext context) throws StartException {
        this.connector = new SecureConnector( socketBindingInjector.getValue().getSocketAddress(), sslContextInjector.getValue() );
        this.connector.setServer( this.stompletServerInjector.getValue() );
        try {
            this.connector.start();
        } catch (Exception e) {
            context.failed( new StartException( e ) );
        }
    }

    @Override
    public void stop(StopContext context) {
        try {
            this.connector.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public Injector<SSLContext> getSSLContextInjector() {
        return this.sslContextInjector;
    }
    
    private InjectedValue<SSLContext> sslContextInjector = new InjectedValue<SSLContext>();

}
