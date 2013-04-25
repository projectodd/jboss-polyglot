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
import java.util.concurrent.ConcurrentMap;

import javax.management.MBeanServer;

import org.jboss.as.jmx.MBeanRegistrationService;
import org.jboss.as.jmx.MBeanServerService;
import org.jboss.as.jmx.ObjectNameFactory;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.logging.Logger;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.AbstractServiceListener;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceBuilder.DependencyType;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceController.Transition;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.ImmediateValue;
import org.jboss.msc.value.InjectedValue;
import org.projectodd.polyglot.core.app.ApplicationMetaData;
import org.projectodd.polyglot.core.util.ClusterUtil;

public class AtRuntimeInstaller<T> implements Service<T>, StartState {

    public static final String HA_SINGLETON_SERVICE_SUFFIX = "ha-singleton";

    public AtRuntimeInstaller(DeploymentUnit unit) {
        this(unit, null);
    }

    public AtRuntimeInstaller(DeploymentUnit unit, ServiceTarget globalServiceTarget) {
        this.unit = unit;
        this.globalServiceTarget = globalServiceTarget;
    }

    protected void replaceService(ServiceName name) {
        replaceService(this.unit.getServiceRegistry(),
                name,
                null);
    }

    protected void replaceService(ServiceName name, Runnable actionOnRemove) {
        replaceService(this.unit.getServiceRegistry(),
                name,
                actionOnRemove);
    }

    @SuppressWarnings("unchecked")
    public static void replaceService(ServiceRegistry registry, ServiceName name, Runnable actionOnRemove) {
        final ServiceController service = registry.getService(name);

        if (service != null) {
            service.addListener(new RemovalListener(actionOnRemove));
            service.setMode(Mode.REMOVE);
        } else if (actionOnRemove != null) {
            actionOnRemove.run();
        }
    }


    protected ServiceBuilder<?> build(final ServiceName serviceName, final Service<?> service, final boolean singleton) {
    	return build(serviceName, service, singleton, "global");
    }
    
    protected ServiceBuilder<?> build(final ServiceName serviceName, final Service<?> service, final boolean singleton, String singletonContext) {	
        ServiceBuilder<?> builder = getTarget().addService(serviceName, service);
        if (singleton && ClusterUtil.isClustered(getUnit().getServiceRegistry())) {
            builder.addDependency(unit.getServiceName().
                    append(HA_SINGLETON_SERVICE_SUFFIX).
                    append(singletonContext));
            builder.setInitialMode(Mode.PASSIVE);
        } else {
            builder.setInitialMode(Mode.ACTIVE);
        }

        return builder;
    }

    protected void deploy(final ServiceName serviceName, final Service<?> service) {
        deploy(serviceName, service, false);
    }

    protected void deploy(final ServiceName serviceName, final Service<?> service, final boolean singleton) {
        replaceService(serviceName, new Runnable() {
            public void run() {
                build(serviceName, service, singleton).install();
            }
        });
    }

    public ServiceName installMBean(final ServiceName name, final String groupName, Object mbean) {
        return installMBean(name, new MBeanRegistrationService<Object>(mbeanName(groupName, name),
                name,
                new ImmediateValue<Object>(mbean)));
    }

    public ServiceName installMBean(final ServiceName name, final MBeanRegistrationService<?> mbeanService) {
        final ServiceName mbeanName = name.append("mbean");

        replaceService(mbeanName, new Runnable() {
            public void run() {
                getTarget().addService(mbeanName, mbeanService).
                        addDependency(DependencyType.OPTIONAL, MBeanServerService.SERVICE_NAME, MBeanServer.class, mbeanService.getMBeanServerInjector()).
                        setInitialMode(Mode.PASSIVE).
                        install();
            }
        });

        return mbeanName;
    }

    @SuppressWarnings("serial")
    public String mbeanName(final String domain, final ServiceName name) {
        final ApplicationMetaData appMetaData = unit.getAttachment(ApplicationMetaData.ATTACHMENT_KEY);
        return ObjectNameFactory.create(domain, new Hashtable<String, String>() {
            {
                put("app", appMetaData.getApplicationName());
                put("name", name.getSimpleName());
            }
        }).toString();
    }


    public boolean inCluster() {
        return ClusterUtil.isClustered(this.unit.getServiceRegistry());
    }
    
    @Override
    public boolean isStarted() {
        return this.running;
    }

    @Override
    public boolean hasStartedAtLeastOnce() {
        return this.hasStarted;
    }

    public String getName() {
        return this.getClass().getName();
    }

    @Override
    public void start(StartContext context) throws StartException {
        this.serviceTarget = context.getChildTarget();
        this.running = true;
        this.hasStarted = true;
    }

    @Override
    public synchronized void stop(StopContext context) {
        this.running = false;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T getValue() {
        return (T) this;
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
    
    @SuppressWarnings("rawtypes")
    public ConcurrentMap getCoordinationMap() {
        return this.coordinationMapInjector.getValue();
    }

    @SuppressWarnings("rawtypes")
    public Injector<ConcurrentMap> getCoordinationMapInjector() {
        return this.coordinationMapInjector;
    }
    
    private DeploymentUnit unit;
    private ServiceTarget serviceTarget;
    private ServiceTarget globalServiceTarget;
    private boolean running = false;
    private boolean hasStarted = false;

    @SuppressWarnings("rawtypes")
    private InjectedValue<ConcurrentMap> coordinationMapInjector = new InjectedValue<ConcurrentMap>();
    
    protected static final Logger log = Logger.getLogger("org.projectodd.polyglot.core");

    @SuppressWarnings("rawtypes")
    public static class RemovalListener extends AbstractServiceListener {

        public RemovalListener() {
        }

        public RemovalListener(Runnable action) {
            this.action = action;
        }

        @Override
        public void transition(ServiceController controller,
                               Transition transition) {
            if (transition == Transition.REMOVING_to_REMOVED) {
                if (action != null) {
                    action.run();
                }
            }
        }

        private Runnable action;
    }
}
