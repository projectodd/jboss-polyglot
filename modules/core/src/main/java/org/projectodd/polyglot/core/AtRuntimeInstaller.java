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

package org.projectodd.polyglot.core;

import java.util.Hashtable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;

import javax.management.MBeanServer;

import org.jboss.as.jmx.MBeanRegistrationService;
import org.jboss.as.jmx.MBeanServerService;
import org.jboss.as.jmx.ObjectNameFactory;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.logging.Logger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceBuilder.DependencyType;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceController.Transition;
import org.jboss.msc.service.ServiceListener;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.ImmediateValue;
import org.projectodd.polyglot.core.app.ApplicationMetaData;
import org.projectodd.polyglot.core.util.ClusterUtil;

public class AtRuntimeInstaller<T> implements Service<T>  {

    public static final String HA_SINGLETON_SERVICE_SUFFIX = "ha-singleton";

    public AtRuntimeInstaller(DeploymentUnit unit) {
        this( unit, null );
    }

    public AtRuntimeInstaller(DeploymentUnit unit, ServiceTarget globalServiceTarget) {
        this.unit = unit;
        this.globalServiceTarget = globalServiceTarget;
    }

    protected ExecutorCompletionService<ServiceController> replaceService(ServiceName name, Runnable actionOnRemove) {
        return replaceService(this.unit.getServiceRegistry(),
                name,
                actionOnRemove);
    }

    @SuppressWarnings("unused")
    protected ExecutorCompletionService<ServiceController> replaceService(ServiceName name, ExecutorCompletionService<ServiceController> completionService) {
        return replaceService(this.unit.getServiceRegistry(),
                name,
                null,
                completionService);
    }

    @SuppressWarnings("unused")
    protected ExecutorCompletionService<ServiceController> replaceService(ServiceName name, Runnable actionOnRemove, ExecutorCompletionService<ServiceController> completionService) {
        return replaceService(this.unit.getServiceRegistry(),
                name,
                actionOnRemove,
                completionService);
    }

    @SuppressWarnings("unchecked")
    public static ExecutorCompletionService<ServiceController> replaceService(ServiceRegistry registry, final ServiceName name, Runnable actionOnRemove) {
        return replaceService(registry, name, actionOnRemove, new ExecutorCompletionService<ServiceController>(Executors.newSingleThreadExecutor()));
    }

    @SuppressWarnings("unchecked")
    public static ExecutorCompletionService<ServiceController> replaceService(ServiceRegistry registry, final ServiceName name, Runnable actionOnRemove, ExecutorCompletionService<ServiceController> completionService) {
        final ServiceController<?> service = registry.getService(name);

        if (service != null) {
            if (actionOnRemove != null) {
                service.addListener(new RemovalListener(completionService, actionOnRemove));
            } else {
                // If there is no actionOnRemove provided but we have a service to remove
                // let's create a dummy action, this will help to watch for the
                // removal completion when using the returned ExecutorCompletionService
                // and its methods
                service.addListener(new RemovalListener(completionService, new Runnable() {
                    @Override
                    public void run() {
                        log.debugf("The '%s' service was removed", name.getCanonicalName());
                    }
                }));
            }
            service.setMode(Mode.REMOVE);
        } else if (actionOnRemove != null) {
            completionService.submit(actionOnRemove, null);
        }

        return completionService;
    }


    protected ServiceBuilder<?> build(final ServiceName serviceName, final Service<?> service, final boolean singleton) {
        ServiceBuilder<?> builder = getTarget().addService(serviceName, service);
        if (singleton && ClusterUtil.isClustered( getUnit().getServiceRegistry() )) {
            builder.addDependency( unit.getServiceName().append( HA_SINGLETON_SERVICE_SUFFIX ) );
            builder.setInitialMode(Mode.PASSIVE);
        } else {
            builder.setInitialMode(Mode.ACTIVE);
        }

        return builder;
    }

    protected void deploy(final ServiceName serviceName, final Service<?> service) {
        deploy( serviceName, service, false );
    }

    protected void deploy(final ServiceName serviceName, final Service<?> service, final boolean singleton) {
        replaceService( serviceName, new Runnable() {
            public void run() {
                build( serviceName, service, singleton ).install();
            }
        });
    }

    public ServiceName installMBean(final ServiceName name, final String groupName, Object mbean) {
        return installMBean( name, new MBeanRegistrationService<Object>( mbeanName( groupName, name ),
                name,
                new ImmediateValue<Object>( mbean ) ) );
    }

    public ServiceName installMBean(final ServiceName name, final MBeanRegistrationService<?> mbeanService) {
        final ServiceName mbeanName = name.append( "mbean" );

        replaceService( mbeanName, new Runnable() {
            public void run() {
                getTarget().addService( mbeanName, mbeanService ).
                addDependency( DependencyType.OPTIONAL, MBeanServerService.SERVICE_NAME, MBeanServer.class, mbeanService.getMBeanServerInjector() ).
                setInitialMode( Mode.PASSIVE ).
                install();
            }
        } );

        return mbeanName;
    }

    @SuppressWarnings("serial")
    public String mbeanName(final String domain, final ServiceName name) {
        final ApplicationMetaData appMetaData = unit.getAttachment( ApplicationMetaData.ATTACHMENT_KEY );
        return ObjectNameFactory.create( domain, new Hashtable<String, String>() {
            {
                put( "app", appMetaData.getApplicationName() );
                put( "name", name.getSimpleName() );
            }
        } ).toString();
    }

    @Override
    public void start(StartContext context) throws StartException {
        this.serviceTarget = context.getChildTarget();
    }

    @Override
    public synchronized void stop(StopContext context) {
    }

    @SuppressWarnings("unchecked")
    @Override
    public T getValue() {
        return (T)this;
    }

    public ServiceTarget getTarget() {
        return this.serviceTarget;
    }

    public DeploymentUnit getUnit() {
        return unit;
    }

    public ServiceTarget getGlobalTarget() {
        return this.globalServiceTarget;
    }

    private DeploymentUnit unit;
    private ServiceTarget serviceTarget;
    private ServiceTarget globalServiceTarget;

    protected static final Logger log = Logger.getLogger( "org.projectodd.polyglot.core" );

    @SuppressWarnings("rawtypes")
    public static class RemovalListener implements ServiceListener {

        public RemovalListener() {
        }

        public RemovalListener(ExecutorCompletionService completionService, Runnable action) {
            this.completionService = completionService;
            this.action = action;
        }

        @Override
        public void transition(ServiceController controller,
                Transition transition) {
            if (transition == Transition.REMOVING_to_REMOVED) {
                if (action != null) {
                    if (completionService != null) {
                        completionService.submit(action, null);
                    } else  {
                        action.run();
                    }
                }

            }
        }

        @Override
        public void listenerAdded(ServiceController controller) {
           // not used
        }

        @Override
        public void serviceRemoveRequested(ServiceController controller) {
           // not used
        }

        @Override
        public void serviceRemoveRequestCleared(ServiceController controller) {
           // not used
        }

        @Override
        public void dependencyFailed(ServiceController controller) {
           // not used
        }

        @Override
        public void dependencyFailureCleared(ServiceController controller) {
           // not used
        }

        @Override
        public void immediateDependencyUnavailable(ServiceController controller) {
           // not used
        }

        @Override
        public void immediateDependencyAvailable(ServiceController controller) {
           // not used
        }

        @Override
        public void transitiveDependencyUnavailable(ServiceController controller) {
           // not used
        }

        @Override
        public void transitiveDependencyAvailable(ServiceController controller) {
           // not used
        }

        private ExecutorCompletionService completionService;
        private Runnable action;
    }
}
