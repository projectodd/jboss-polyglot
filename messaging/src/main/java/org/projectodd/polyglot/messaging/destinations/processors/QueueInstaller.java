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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.hornetq.jms.server.JMSServerManager;
import org.jboss.as.messaging.MessagingServices;
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
    public static DestroyableJMSQueueService deployGlobalQueue(ServiceRegistry registry, final ServiceTarget serviceTarget, 
                                                               final String queueName, final boolean durable, 
                                                               final String selector, final String[] jndiNames) {
        ServiceName globalQServiceName = queueServiceName( queueName );
        ServiceController globalQService = registry.getService( globalQServiceName );
        DestroyableJMSQueueService globalQ;
        
        if (globalQService == null) {
            globalQ = new DestroyableJMSQueueService(queueName, selector, durable, jndiNames);
            deployGlobalQueue(serviceTarget, globalQ, queueName);    
        } else {
            globalQ = (DestroyableJMSQueueService)globalQService.getService();
            ReconfigurationValidator validator = new ReconfigurationValidator(globalQ, durable, selector, jndiNames);
            if (globalQ.getReferenceCount().intValue() == 0) {
                // It should be stopping - wait
                try {
                    if (!globalQ.getStopLatch().await(1, TimeUnit.MINUTES)) {
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
                final DestroyableJMSQueueService finalGlobalQ = globalQ;
                
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
        
        return globalQ;
    }
    
    protected ServiceName deploy(DeploymentUnit unit, ServiceTarget serviceTarget, QueueMetaData queue) {
        String[] jndis = DestinationUtils.jndiNames(queue.getName(), queue.isExported());

        log.debugf("JNDI names to bind the '%s' queue to: %s", queue.getName(), Arrays.toString(jndis));

        DestroyableJMSQueueService global = deployGlobalQueue(unit.getServiceRegistry(),
                                                              this.globalTarget,
                                                              queue.getName(), 
                                                              queue.isDurable(),
                                                              queue.getSelector(), 
                                                              jndis);
        
        return DestinationUtils.deployDestinationPointerService(unit, serviceTarget, queue.getName(), 
                                                                queueServiceName(queue.getName()),
                                                                global.getReferenceCount());
    }   

    @Override
    public void undeploy(DeploymentUnit context) {

    }

    private ServiceTarget globalTarget;
    
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
            this.from.append(key + ": " + from);
            this.to.append(key + ": " + to);
        }
        
        public boolean isReconfigure() {
            return this.reconfigure;
        }
        
        protected boolean reconfigure = false;
        private StringBuilder from = new StringBuilder();
        private StringBuilder to = new StringBuilder();
    }
}

