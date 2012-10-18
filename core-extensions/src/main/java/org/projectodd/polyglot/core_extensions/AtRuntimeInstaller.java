/*
 * Copyright 2008-2012 Red Hat, Inc, and individual contributors.
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

package org.projectodd.polyglot.core_extensions;

import java.util.Hashtable;

import javax.management.MBeanServer;

import org.jboss.as.jmx.MBeanRegistrationService;
import org.jboss.as.jmx.MBeanServerService;
import org.jboss.as.jmx.ObjectNameFactory;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceBuilder.DependencyType;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceController.Transition;
import org.jboss.msc.service.ServiceListener;
import org.jboss.msc.service.ServiceName;
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
        this.unit = unit;
    }

    @SuppressWarnings("unchecked")
    protected void replaceService(ServiceName name, Runnable actionOnRemove) {
        ServiceController<?> service = this.unit.getServiceRegistry().getService( name ); 
        
        if (service != null) {
            if (actionOnRemove != null) {
                service.addListener( new RemovalListener( actionOnRemove ) );
            }
            service.setMode( Mode.REMOVE );
        } else if (actionOnRemove != null) {
            actionOnRemove.run();
        }
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

    private DeploymentUnit unit;
    private ServiceTarget serviceTarget;
    
    @SuppressWarnings("rawtypes")
    public class RemovalListener implements ServiceListener {

        public RemovalListener(Runnable action) {
            this.action = action;
        }

        @Override
        public void transition(ServiceController controller,
                Transition transition) {
            if (transition == Transition.REMOVING_to_REMOVED) {
                this.action.run();
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
     
        private Runnable action; 
    }
}
