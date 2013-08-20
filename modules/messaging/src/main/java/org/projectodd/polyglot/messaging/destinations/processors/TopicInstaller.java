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
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.projectodd.polyglot.core.AtRuntimeInstaller;
import org.projectodd.polyglot.core.ServiceSynchronizationManager;
import org.projectodd.polyglot.messaging.destinations.DestinationService;
import org.projectodd.polyglot.messaging.destinations.DestinationUtils;
import org.projectodd.polyglot.messaging.destinations.DestroyableJMSTopicService;
import org.projectodd.polyglot.messaging.destinations.HornetQStartupPoolService;
import org.projectodd.polyglot.messaging.destinations.TopicMetaData;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.projectodd.polyglot.core.ServiceSynchronizationManager.INSTANCE;
import static org.projectodd.polyglot.core.ServiceSynchronizationManager.State;

public class TopicInstaller implements DeploymentUnitProcessor {

    public TopicInstaller(ServiceTarget globalTarget) {
        this.globalTarget = globalTarget;
    }
    
    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit unit = phaseContext.getDeploymentUnit();

        List<TopicMetaData> allMetaData = unit.getAttachmentList( TopicMetaData.ATTACHMENTS_KEY );

        for (TopicMetaData each : allMetaData) {
            if (!each.isRemote())
                deploy( phaseContext.getDeploymentUnit(),
                        phaseContext.getServiceTarget(),
                        this.globalTarget,
                        each );
        }

    }

    public static ServiceName topicServiceName(String name) {
        return JMSServices.getJmsTopicBaseServiceName(MessagingServices.getHornetQServiceName( "default" ))
                .append( name );
    }
    
    private static ServiceName deployGlobalTopic(ServiceTarget serviceTarget, DestroyableJMSTopicService service, String name) {
        final ServiceName hornetQserviceName = MessagingServices.getHornetQServiceName("default");
        final ServiceName serviceName = topicServiceName(name);
        ServiceSynchronizationManager mgr = INSTANCE;

        mgr.addService(serviceName, service, true);

        serviceTarget.addService(serviceName, service)
          .addDependency(JMSServices.getJmsManagerBaseServiceName( hornetQserviceName ), JMSServerManager.class, service.getJmsServer() )
          .addDependency( HornetQStartupPoolService.getServiceName( hornetQserviceName ), ExecutorService.class, service.getExecutorServiceInjector() )
          .addListener(mgr)
          .setInitialMode( Mode.ON_DEMAND )
          .install();
        
        if (!mgr.waitForServiceStart(serviceName,
                                     WAIT_TIMEOUT)) {
            log.warn("Timed out waiting for " + name + " to start");
        }

        return serviceName;

    }

    @SuppressWarnings("rawtypes")
    public static synchronized ServiceName deployGlobalTopic(ServiceRegistry registry,
                                                             final ServiceTarget serviceTarget,
                                                             final String topicName,
                                                             final String[] jndiNames) {
        ServiceSynchronizationManager mgr = INSTANCE;
        ServiceName globalTServiceName = topicServiceName( topicName );
        DestroyableJMSTopicService globalT = (DestroyableJMSTopicService)mgr.getService(globalTServiceName);

        if (globalT == null) {
            // if it exists but we don't know about it, it's container managed
            if (registry.getService(globalTServiceName) == null) {
                globalT = new DestroyableJMSTopicService(topicName, jndiNames);
                deployGlobalTopic(serviceTarget, globalT, topicName);
            }
        } else {
            ReconfigurationValidator validator = new ReconfigurationValidator(globalT,
                                                                              jndiNames);
                        
            if (!mgr.hasDependents(globalT)) {
                // It should be stopping - wait
                if (!mgr.waitForServiceDown(globalTServiceName,
                                            WAIT_TIMEOUT)) {
                    log.warn("Timed out waiting for inactive " + topicName + " to stop");
                }

                if (validator.isReconfigure()) {
                    log.infof("Reconfiguring %s from %s to %s",
                              topicName, validator.fromMsg(), validator.toMsg());
                }
                
                globalT = new DestroyableJMSTopicService(topicName, jndiNames);
                final DestroyableJMSTopicService finalGlobalT = globalT;
                
                AtRuntimeInstaller.replaceService(registry, globalTServiceName, new Runnable() {
                    public void run() {
                        deployGlobalTopic(serviceTarget, finalGlobalT, topicName);
                    }
                });
            } else if (validator.isReconfigure()) {
                log.warnf("Ignoring attempt to reconfigure %s from %s to %s - it is currently active",
                          topicName, validator.fromMsg(), validator.toMsg());
            }
        }

        return globalTServiceName;
    }

    public static synchronized ServiceName deploy(DeploymentUnit unit,
                                                  ServiceTarget serviceTarget,
                                                  ServiceTarget globalTarget,
                                                  TopicMetaData topic) {
        String[] jndis = DestinationUtils.jndiNames(topic.getName(), topic.isExported());

        log.debugf("JNDI names to bind the '%s' topic to: %s", topic.getName(), Arrays.toString(jndis));

        ServiceName pointerName = DestinationUtils.destinationPointerName(unit,
                                                                          topic.getName());
        DestinationService service = new DestinationService(pointerName);
        ServiceSynchronizationManager mgr = INSTANCE;

        ServiceName globalTName = deployGlobalTopic(unit.getServiceRegistry(),
                                                    globalTarget,
                                                    topic.getName(),
                                                    jndis);
        
        if (!mgr.hasService(pointerName)) {
            mgr.addService(pointerName, service, globalTName);

            serviceTarget.addService(pointerName, service)
                    .addDependency(topicServiceName(topic.getName()))
                    .setInitialMode(Mode.ACTIVE)
                    .addListener(mgr)
                    .install();


        } else {
            log.warn(pointerName + " already started");
        }

        if (!mgr.waitForServiceStart(pointerName, WAIT_TIMEOUT)) {
            log.warn("Timed out waiting for " + topic.getName() + " pointer to start");
        }

        return pointerName;
    }

    @Override
    public void undeploy(DeploymentUnit context) {
        // TODO Auto-generated method stub

    }

    private ServiceTarget globalTarget;

    final static long WAIT_TIMEOUT = DestinationUtils.destinationWaitTimeout();

    static final Logger log = Logger.getLogger( "org.projectodd.polyglot.messaging" );

    protected static class ReconfigurationValidator extends QueueInstaller.ReconfigurationValidator {
        ReconfigurationValidator(DestroyableJMSTopicService actual, String[] jndi) {
            boolean jndiEqual = jndiEqual(actual.getJndi(), jndi);
            this.reconfigure = !jndiEqual;
            
            if (!jndiEqual)
                add("jndi", actual.getJndi(), jndi);
        }
    }
}
