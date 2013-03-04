package org.projectodd.polyglot.stomp;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.ProtocolHandler;
import org.jboss.logging.Logger;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

public class SSLContextService implements Service<SSLContext> {

    @Override
    public SSLContext getValue() throws IllegalStateException, IllegalArgumentException {
        return this.sslContext;
    }

    @Override
    public void start(StartContext context) throws StartException {
        Connector connector = connectorInjector.getValue();

        ProtocolHandler handler = connector.getProtocolHandler();
        String keystorePath = (String) handler.getAttribute( "keystore" );
        String keystorePassword = (String) handler.getAttribute( "keypass" );
        String keystoreType = (String) handler.getAttribute( "keystoreType" );
        String protocols = (String) handler.getAttribute( "protocols" );
        String algorithm = (String) handler.getAttribute( "algorithm" );

        if ( protocols == null ) {
            protocols = "TLS";
        }
        if ( keystoreType == null) {
            keystoreType = "JKS";
        }
        if ( algorithm == null ) {
            algorithm = "SunX509";
        }
        
        try {
            this.sslContext = SSLContext.getInstance( protocols );

            KeyStore keyStore = KeyStore.getInstance( keystoreType );
            InputStream stream = new FileInputStream( keystorePath );
            try {
                keyStore.load( stream, keystorePassword.toCharArray() );
            } finally {
                stream.close();
            }
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance( algorithm );
            keyManagerFactory.init( keyStore, keystorePassword.toCharArray() );
            this.sslContext.init( keyManagerFactory.getKeyManagers(), null, null );
        } catch (NoSuchAlgorithmException e) {
            throw new StartException( e );
        } catch (KeyManagementException e) {
            throw new StartException( e );
        } catch (KeyStoreException e) {
            throw new StartException( e );
        } catch (UnrecoverableKeyException e) {
            throw new StartException( e );
        } catch (FileNotFoundException e) {
            throw new StartException( e );
        } catch (CertificateException e) {
            throw new StartException( e );
        } catch (IOException e) {
            throw new StartException( e );
        }
    }

    @Override
    public void stop(StopContext context) {
        this.sslContext = null;
    }

    public Injector<Connector> getWebConnectorInjector() {
        return this.connectorInjector;
    }

    private SSLContext sslContext;
    private InjectedValue<Connector> connectorInjector = new InjectedValue<Connector>();
    static final Logger log = Logger.getLogger( "org.projectodd.polyglot.stomp.as" );

}
