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

package org.projectodd.polyglot.messaging.destinations.processors;

import org.hornetq.jms.server.JMSServerManager;
import org.jboss.as.messaging.MessagingServices;
import org.jboss.as.messaging.jms.JMSServices;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.projectodd.polyglot.core.AtRuntimeInstaller;
import org.projectodd.polyglot.core.ServiceSynchronizationManager;
import org.projectodd.polyglot.messaging.destinations.DestinationPointerService;
import org.projectodd.polyglot.messaging.destinations.DestinationService;
import org.projectodd.polyglot.messaging.destinations.DestinationUtils;
import org.projectodd.polyglot.messaging.destinations.HornetQStartupPoolService;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;

public class DestinationInstaller {
    final static long WAIT_TIMEOUT = DestinationUtils.destinationWaitTimeout();

    public DestinationInstaller(ServiceTarget globalTarget) {
        this.globalTarget = globalTarget;
    }

    protected ServiceTarget globalTarget;

    protected static ServiceName deployGlobalDestination(ServiceTarget serviceTarget,
                                                         DestinationService service,
                                                         ServiceName serviceName,
                                                         String name,
                                                         boolean waitForStart) {
        final ServiceName hornetQserviceName = MessagingServices.getHornetQServiceName("default");
        ServiceSynchronizationManager mgr = ServiceSynchronizationManager.INSTANCE;

        mgr.addService(serviceName, service, true);

        serviceTarget.addService(serviceName, service)
          .addDependency( JMSServices.getJmsManagerBaseServiceName(hornetQserviceName), JMSServerManager.class, service.getJmsServer() )
          .addDependency( HornetQStartupPoolService.getServiceName(hornetQserviceName), ExecutorService.class, service.getExecutorServiceInjector() )
          .addListener(mgr)
          .setInitialMode(ServiceController.Mode.ON_DEMAND)
          .install();

        if (waitForStart) {
            if (!mgr.waitForServiceStart(serviceName, WAIT_TIMEOUT)) {
                log.warn("Timed out waiting for " + name + " to start");
            }
        }

        return serviceName;
    }

    static final Logger log = Logger.getLogger("org.projectodd.polyglot.messaging");

    @SuppressWarnings("rawtypes")
    public static synchronized ServiceName deployGlobalDestination(ServiceRegistry registry,
                                                                   final ServiceTarget serviceTarget,
                                                                   final ServiceName globalServiceName,
                                                                   final String destinationName,
                                                                   final DestinationServiceFactory destinationServiceFactory,
                                                                   final ValidatorFactory validatorFactory,
                                                                   final boolean waitForStart) {
        ServiceSynchronizationManager mgr = ServiceSynchronizationManager.INSTANCE;
        DestinationService globalD = (DestinationService)mgr.getService(globalServiceName);

        if (globalD == null) {
            // if it exists but we don't know about it, it's container managed
            if (registry.getService(globalServiceName) == null) {
                globalD = destinationServiceFactory.newService();
                deployGlobalDestination(serviceTarget, globalD, globalServiceName, destinationName, waitForStart);
            }
        } else {
            ReconfigurationValidator validator = validatorFactory.newValidator(globalD);

            if (!mgr.hasDependents(globalD)) {
                // It should be stopping - wait
                if (!mgr.waitForServiceDown(globalServiceName,
                                            WAIT_TIMEOUT)) {
                    log.warn("Timed out waiting for inactive " + destinationName + " to stop");
                }

                if (validator.isReconfigure()) {
                    log.infof("Reconfiguring %s from %s to %s",
                              destinationName, validator.fromMsg(), validator.toMsg());
                }

                globalD = destinationServiceFactory.newService();
                final DestinationService finalGlobalD = globalD;

                AtRuntimeInstaller.replaceService(registry, globalServiceName, new Runnable() {
                    public void run() {
                        deployGlobalDestination(serviceTarget, finalGlobalD, globalServiceName, destinationName, waitForStart);
                    }
                });
            } else if (validator.isReconfigure()){
                log.warnf("Ignoring attempt to reconfigure %s from %s to %s - it is currently active",
                          destinationName, validator.fromMsg(), validator.toMsg());
            }

        }

        return globalServiceName;
    }

    protected static ServiceName deploy(final DeploymentUnit unit,
                                        final ServiceTarget serviceTarget,
                                        final ServiceTarget globalTarget,
                                        final String destinationName,
                                        final ServiceName destinationServiceName,
                                        final DestinationServiceFactory destinationServiceFactory,
                                        final ValidatorFactory validatorFactory,
                                        final boolean waitForStart) {

        ServiceName pointerName = DestinationUtils.destinationPointerName(unit, destinationName);
        DestinationPointerService service = new DestinationPointerService(pointerName);
        ServiceSynchronizationManager mgr = ServiceSynchronizationManager.INSTANCE;

        ServiceName globalQName =
                deployGlobalDestination(unit.getServiceRegistry(),
                                        globalTarget,
                                        destinationServiceName,
                                        destinationName,
                                        destinationServiceFactory,
                                        validatorFactory,
                                        waitForStart);

        if (!mgr.hasService(pointerName)) {
            mgr.addService(pointerName, service, globalQName);

            serviceTarget.addService(pointerName, service)
                    .addDependency(destinationServiceName)
                    .setInitialMode(ServiceController.Mode.ACTIVE)
                    .addListener(mgr)
                    .install();
        } else {
            log.warn(pointerName + " already started");
        }

        if (waitForStart) {
            if (!mgr.waitForServiceStart(pointerName, WAIT_TIMEOUT)) {
                log.warn("Timed out waiting for " + destinationName + " pointer to start");
            }
        }

        return pointerName;
    }

    protected static class ReconfigurationValidator {
        ReconfigurationValidator() {}

        public String fromMsg() {
            return this.from.toString();
        }

        public String toMsg() {
            return this.to.toString();
        }

        protected boolean jndiEqual(String[] actual, String[] update) {
            String[] sortedJndi = update.clone();
            String[] actualJndi = actual.clone();
            Arrays.sort(sortedJndi);
            Arrays.sort(actualJndi);
            return Arrays.equals(sortedJndi, actualJndi);
        }

        protected void add(String key, Object from, Object to) {
            if (this.from.length() > 0) {
                this.from.append(", ");
                this.to.append(", ");
            }
            this.from.append(key).append(": ").append(from);
            this.to.append(key).append(": ").append(to);
        }

        public boolean isReconfigure() {
            return this.reconfigure;
        }

        protected boolean reconfigure = false;
        private StringBuilder from = new StringBuilder();
        private StringBuilder to = new StringBuilder();
    }

    protected interface DestinationServiceFactory {

        public DestinationService newService();

    }

    protected interface ValidatorFactory {
        public ReconfigurationValidator newValidator(DestinationService service);
    }
}
