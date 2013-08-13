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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.hornetq.jms.server.JMSServerManager;
import org.jboss.as.messaging.MessagingServices;
import org.jboss.as.messaging.jms.JMSQueueService;
import org.jboss.as.messaging.jms.JMSServices;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.projectodd.polyglot.core.AtRuntimeInstaller;
import org.projectodd.polyglot.messaging.destinations.DestinationUtils;
import org.projectodd.polyglot.messaging.destinations.DestroyableJMSQueueService;
import org.projectodd.polyglot.messaging.destinations.HornetQStartupPoolService;
import org.projectodd.polyglot.messaging.destinations.QueueMetaData;

public class QueueInstaller implements DeploymentUnitProcessor {

    public QueueInstaller(ServiceTarget globalTarget) {
        this.globalTarget = globalTarget;
    }

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit unit = phaseContext.getDeploymentUnit();

        List<QueueMetaData> allMetaData = unit.getAttachmentList( QueueMetaData.ATTACHMENTS_KEY );

        for (QueueMetaData each : allMetaData) {
            if (!each.isRemote())
                deploy( phaseContext.getDeploymentUnit(), phaseContext.getServiceTarget(), each );
        }

    }

    public static ServiceName queueServiceName(String name) {
        return JMSServices.getJmsQueueBaseServiceName(MessagingServices.getHornetQServiceName( "default" ))
                .append( name );
    }
    
    private static ServiceName deployGlobalQueue(ServiceTarget serviceTarget, DestroyableJMSQueueService service, String name) {
        final ServiceName hornetQserviceName = MessagingServices.getHornetQServiceName( "default" );
        final ServiceName serviceName = queueServiceName( name );
        serviceTarget.addService(serviceName, service)
          .addDependency( JMSServices.getJmsManagerBaseServiceName( hornetQserviceName ), JMSServerManager.class, service.getJmsServer() )
          .addDependency( HornetQStartupPoolService.getServiceName( hornetQserviceName ), ExecutorService.class, service.getExecutorServiceInjector() )
          .setInitialMode( Mode.ON_DEMAND )
          .install();

        return serviceName;
    }

    @SuppressWarnings("rawtypes")
    public static synchronized JMSQueueService deployGlobalQueue(ServiceRegistry registry, final ServiceTarget serviceTarget,
                                                                 final String queueName, final boolean durable,
                                                                 final String selector, final String[] jndiNames) {
        ServiceName globalQServiceName = queueServiceName(queueName);
        ServiceController globalQService = registry.getService(globalQServiceName);
        JMSQueueService globalQ;

        if (startedQueues.contains(queueName) &&
                globalQService == null) {
            //we've started this queue already, but it hasn't yet made it to the MSC
            int retries = 0;
            while (globalQService == null &&
                    retries < 10000) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException ignored) {}
                globalQService = registry.getService(globalQServiceName);
                retries++;
            }
        }

        startedQueues.add(queueName);

        if (globalQService == null) {
            globalQ = new DestroyableJMSQueueService(queueName, selector, durable, jndiNames);
            deployGlobalQueue(serviceTarget,(DestroyableJMSQueueService)globalQ, queueName);
        } else {
            globalQ = (JMSQueueService)globalQService.getService();
            if (globalQ instanceof DestroyableJMSQueueService &&
                    ((DestroyableJMSQueueService)globalQ).hasStarted()) {
                DestroyableJMSQueueService destroyableQ = (DestroyableJMSQueueService)globalQ;
                ReconfigurationValidator validator = new ReconfigurationValidator(destroyableQ,
                                                                                  durable, selector, jndiNames);
                if (destroyableQ.getReferenceCount().intValue() == 0) {
                    // It should be stopping - wait
                    try {
                        if (!destroyableQ.getStopLatch().await(1, TimeUnit.MINUTES)) {
                            log.warn("Timed out waiting for inactive " + queueName + " to stop");
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    if (validator.isReconfigure()) {
                        log.infof("Reconfiguring %s from %s to %s",
                                queueName, validator.fromMsg(), validator.toMsg());
                    }

                    globalQ = new DestroyableJMSQueueService(queueName, selector, durable, jndiNames);
                    final DestroyableJMSQueueService finalGlobalQ = (DestroyableJMSQueueService)globalQ;

                    AtRuntimeInstaller.replaceService(registry, globalQServiceName, new Runnable() {
                        public void run() {
                            deployGlobalQueue(serviceTarget, finalGlobalQ, queueName);
                            try {
                                if (!finalGlobalQ.getStartLatch().await(1, TimeUnit.MINUTES)) {
                                    log.warn("Timed out waiting for " + queueName + " to start");
                                }
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                } else if (validator.isReconfigure()){
                    log.warnf("Ignoring attempt to reconfigure %s from %s to %s - it is currently active",
                            queueName, validator.fromMsg(), validator.toMsg());
                }

            }
        }
        
        return globalQ;
    }

    public static void notifyStop(String name) {
        startedQueues.remove(name);
    }

    protected ServiceName deploy(DeploymentUnit unit, ServiceTarget serviceTarget, QueueMetaData queue) {
        String[] jndis = DestinationUtils.jndiNames(queue.getName(), queue.isExported());

        log.debugf("JNDI names to bind the '%s' queue to: %s", queue.getName(), Arrays.toString(jndis));

        JMSQueueService global = deployGlobalQueue(unit.getServiceRegistry(),
                                                   this.globalTarget,
                                                   queue.getName(),
                                                   queue.isDurable(),
                                                   queue.getSelector(),
                                                   jndis);
        
        return DestinationUtils
                .deployDestinationPointerService(unit, serviceTarget, queue.getName(),
                                                 queueServiceName(queue.getName()),
                                                 global instanceof DestroyableJMSQueueService ?
                                                         ((DestroyableJMSQueueService) global).getReferenceCount() :
                                                         null);
    }

    @Override
    public void undeploy(DeploymentUnit context) {

    }

    private ServiceTarget globalTarget;

    private static Set<String> startedQueues = new HashSet<String>();

    static final Logger log = Logger.getLogger( "org.projectodd.polyglot.messaging" );
    
    protected static class ReconfigurationValidator {
        ReconfigurationValidator() {}
        
        ReconfigurationValidator(DestroyableJMSQueueService actual, boolean durable, String selector, String[] jndi) {
            boolean jndiEqual = jndiEqual(actual.getJndi(), jndi);
            boolean selectorEqual = (selector == null && actual.getSelector() == null) ||
                                       (selector != null && selector.equals(actual.getSelector()));        
             this.reconfigure = actual.isDurable() != durable || 
                    !selectorEqual ||
                    !jndiEqual;
            
            if (actual.isDurable() != durable)
                add("durable", actual.isDurable(), durable);
            
            if (!selectorEqual)
                add("selector", actual.getSelector(), selector);
            
            if (!jndiEqual)
                add("jndi", actual.getJndi(), jndi);
        }
        
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
}

