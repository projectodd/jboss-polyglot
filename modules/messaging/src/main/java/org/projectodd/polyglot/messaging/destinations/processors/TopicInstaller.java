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

import org.jboss.as.messaging.MessagingServices;
import org.jboss.as.messaging.jms.JMSServices;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.projectodd.polyglot.messaging.destinations.DestinationService;
import org.projectodd.polyglot.messaging.destinations.DestinationUtils;
import org.projectodd.polyglot.messaging.destinations.DestroyableJMSTopicService;
import org.projectodd.polyglot.messaging.destinations.TopicMetaData;

import java.util.Arrays;
import java.util.List;

public class TopicInstaller extends DestinationInstaller implements DeploymentUnitProcessor {

    public TopicInstaller(ServiceTarget globalTarget) {
        super(globalTarget);
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

    public static synchronized ServiceName deploy(DeploymentUnit unit,
                                                  ServiceTarget serviceTarget,
                                                  ServiceTarget globalTarget,
                                                  final TopicMetaData topic) {
        final String[] jndis = DestinationUtils.jndiNames(topic.getName(), topic.isExported());

        log.debugf("JNDI names to bind the '%s' topic to: %s", topic.getName(), Arrays.toString(jndis));

        return deploy(unit,
                      serviceTarget,
                      globalTarget,
                      topic.getName(),
                      topicServiceName(topic.getName()),
                      new DestinationServiceFactory() {
                          public DestinationService newService() {
                              return new DestroyableJMSTopicService(topic.getName(),
                                                                    jndis);
                          }
                      },
                      new ValidatorFactory() {
                          @Override
                          public ReconfigurationValidator newValidator(DestinationService service) {
                              return new TopicReconfigurationValidator((DestroyableJMSTopicService)service,
                                                                       jndis);
                          }
                      });
    }

    @Override
    public void undeploy(DeploymentUnit context) {
        // TODO Auto-generated method stub

    }

    static final Logger log = Logger.getLogger( "org.projectodd.polyglot.messaging" );

    protected static class TopicReconfigurationValidator extends ReconfigurationValidator {
        TopicReconfigurationValidator(DestroyableJMSTopicService actual, String[] jndi) {
            boolean jndiEqual = jndiEqual(actual.getJndi(), jndi);
            this.reconfigure = !jndiEqual;
            
            if (!jndiEqual)
                add("jndi", actual.getJndi(), jndi);
        }
    }
}
