package org.projectodd.polyglot.cache.as;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.infinispan.manager.CacheContainer;
import org.jboss.logging.Logger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.projectodd.polyglot.core.util.ClusterUtil;

public class CacheService implements Service<CacheService> {
    
    public static final ServiceName CACHE = ServiceName.of( "polyglot" ).append( "cache" );

    private CacheContainer container;
    private StartContext startContext;
    private String cacheContainerName = "polyglot";
    
    @Override
    public CacheService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    @Override
    public void start(StartContext context) throws StartException {
        startContext = context;
    }

    @Override
    public void stop(StopContext context) {
        if (container != null) { container.stop(); }
    }

    public synchronized CacheContainer getCacheContainer() {
        if (container == null) { lookupContainerService(); }
        return container;
    }

    public boolean isClustered() {
        return (startContext == null) ? false : ClusterUtil.isClustered( startContext.getController().getServiceContainer() );
    }

    public void setCacheContainerName(String cacheContainerName) {
        this.cacheContainerName = cacheContainerName;
    }
    
    private void lookupContainerService() {
        try {
            InitialContext initialContext = new InitialContext();
            container = (CacheContainer) initialContext.lookup( infinispanServiceName() );
            if (container != null) {
                log.info( "Starting polyglot cache service: " + infinispanServiceName() );
                container.start(); 
            } 
        } catch (NamingException e) {
            log.error( "Cannot get cache container. ", e );
        }
    }

    private String infinispanServiceName() {
        return this.isClustered() ?  "java:jboss/infinispan/container/web" : "java:jboss/infinispan/container/" + cacheContainerName;
    }
    
    static final Logger log = Logger.getLogger( "org.projectodd.polyglot.cache.as" );
}
