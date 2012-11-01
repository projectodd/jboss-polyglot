package org.projectodd.polyglot.messaging.destinations;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * Copy of the HornetQStartupPoolService that exists in EAP 6.0 but not
 * any of our AS 7.1.x.incremental builds.
 *
 */
public class HornetQStartupPoolService implements Service<Executor> {
    private volatile ExecutorService executor;

    public static ServiceName getServiceName(ServiceName hornetQServiceName) {
        return hornetQServiceName.append( "polyglot-startup-pool" );
    }

    public void start(StartContext context) throws StartException {
        executor = Executors.newCachedThreadPool();
    }

    public synchronized void stop(StopContext context) {
        executor.shutdown();
        executor = null;
    }

    public Executor getValue() {
        return executor;
    }

}
