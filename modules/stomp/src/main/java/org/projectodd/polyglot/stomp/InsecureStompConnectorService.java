package org.projectodd.polyglot.stomp;

import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.projectodd.stilts.stomp.server.InsecureConnector;

public class InsecureStompConnectorService extends AbstractStompConnectorService {

    @Override
    public void start(StartContext context) throws StartException {
        this.connector = new InsecureConnector( socketBindingInjector.getValue().getSocketAddress() );
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

}
