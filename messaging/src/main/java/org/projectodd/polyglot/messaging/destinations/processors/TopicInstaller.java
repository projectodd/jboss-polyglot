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
import org.jboss.msc.service.ServiceController.State;
import org.jboss.msc.service.ServiceController.Substate;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.projectodd.polyglot.core.AtRuntimeInstaller;
import org.projectodd.polyglot.messaging.destinations.DestinationUtils;
import org.projectodd.polyglot.messaging.destinations.DestroyableJMSQueueService;
import org.projectodd.polyglot.messaging.destinations.DestroyableJMSTopicService;
import org.projectodd.polyglot.messaging.destinations.HornetQStartupPoolService;
import org.projectodd.polyglot.messaging.destinations.TopicMetaData;
import org.projectodd.polyglot.messaging.destinations.processors.QueueInstaller.ReconfigurationValidator;

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
                deploy( phaseContext.getDeploymentUnit(), phaseContext.getServiceTarget(), each );
        }

    }

    public static ServiceName topicServiceName(String name) {
        return JMSServices.getJmsTopicBaseServiceName(MessagingServices.getHornetQServiceName( "default" ))
                .append( name );
    }
    
    private static ServiceName deployGlobalTopic(ServiceTarget serviceTarget, DestroyableJMSTopicService service, String name) {
        final ServiceName hornetQserviceName = MessagingServices.getHornetQServiceName( "default" );
        final ServiceName serviceName = topicServiceName(name);

        serviceTarget.addService(serviceName, service)
          .addDependency(JMSServices.getJmsManagerBaseServiceName( hornetQserviceName ), JMSServerManager.class, service.getJmsServer() )
          .addDependency( HornetQStartupPoolService.getServiceName( hornetQserviceName ), ExecutorService.class, service.getExecutorServiceInjector() )
          .setInitialMode( Mode.ON_DEMAND )
          .install();
        
        return serviceName;

    }

    @SuppressWarnings("rawtypes")
    public static ServiceName deployGlobalTopic(ServiceRegistry registry, final ServiceTarget serviceTarget,
                                                final String topicName, final String[] jndiNames) {
        ServiceName globalTServiceName = topicServiceName( topicName );
        ServiceController globalTService = registry.getService( globalTServiceName );
        
        if (globalTService == null) {
            deployGlobalTopic(serviceTarget,
                              new DestroyableJMSTopicService(topicName, jndiNames),
                              topicName);    
        } else {
            //handle reconfiguration of an existing Topic
            DestroyableJMSTopicService actual = (DestroyableJMSTopicService)globalTService.getService();
            ReconfigurationValidator validator = new ReconfigurationValidator(actual, jndiNames);
            State currentState = globalTService.getState();
            
            if (currentState == State.DOWN || 
                    currentState == State.STOPPING ||
                    globalTService.getSubstate() == Substate.STOP_REQUESTED) {
                if (validator.isReconfigure()) {
                    log.infof("Reconfiguring %s from %s to %s",
                              topicName, validator.fromMsg(), validator.toMsg());
                }
                AtRuntimeInstaller.replaceService(registry, globalTServiceName, new Runnable() {
                    public void run() {
                        deployGlobalTopic(serviceTarget,
                                          new DestroyableJMSTopicService(topicName, jndiNames),
                                          topicName);
                    }
                });
            } 

            if (!validator.isReconfigure()) {
                log.warnf("Ignoring attempt to reconfigure %s from %s to %s - it is currently active",
                          topicName, validator.fromMsg(), validator.toMsg());
            }
        }   
            
        return globalTServiceName;
    }
    
    protected ServiceName deploy(DeploymentUnit unit, ServiceTarget serviceTarget, TopicMetaData topic) {
        String[] jndis = DestinationUtils.jndiNames(topic.getName(), topic.isExported());

        log.debugf("JNDI names to bind the '%s' topic to: %s", topic.getName(), Arrays.toString(jndis));

        ServiceName globalName = deployGlobalTopic(unit.getServiceRegistry(),
                                                   this.globalTarget,
                                                   topic.getName(),
                                                   jndis);
        
        return DestinationUtils.deployDestinationPointerService(unit, serviceTarget, topic.getName(), globalName);
    }

    @Override
    public void undeploy(DeploymentUnit context) {
        // TODO Auto-generated method stub

    }

    private ServiceTarget globalTarget;
    
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
