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
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.projectodd.polyglot.messaging.destinations.DestinationService;
import org.projectodd.polyglot.messaging.destinations.DestinationUtils;
import org.projectodd.polyglot.messaging.destinations.DestroyableJMSTopicService;
import org.projectodd.polyglot.messaging.destinations.TopicMetaData;

import java.util.Arrays;

public class TopicInstaller extends DestinationInstaller {

    public TopicInstaller(ServiceTarget globalTarget) {
        super(globalTarget);
    }

    public static ServiceName topicServiceName(String name) {
        return JMSServices.getJmsTopicBaseServiceName(MessagingServices.getHornetQServiceName( "default" ))
                .append( name );
    }


    public static ServiceName deploySync(DeploymentUnit unit,
                                         ServiceTarget serviceTarget,
                                         ServiceTarget globalTarget,
                                         String name,
                                         boolean exported) {
       return deploy(unit, serviceTarget, globalTarget, true, name, exported);


    }

    public static ServiceName deployAsync(DeploymentUnit unit,
                                          ServiceTarget serviceTarget,
                                          ServiceTarget globalTarget,
                                          String name,
                                          boolean exported) {
        return deploy(unit, serviceTarget, globalTarget, false, name, exported);


    }

    private static synchronized ServiceName deploy(DeploymentUnit unit,
                                                  ServiceTarget serviceTarget,
                                                  ServiceTarget globalTarget,
                                                  boolean waitForStart,
                                                  final String name,
                                                  boolean exported) {
        final String[] jndis = DestinationUtils.jndiNames(name, exported);

        log.debugf("JNDI names to bind the '%s' topic to: %s", name, Arrays.toString(jndis));

        return deploy(unit,
                      serviceTarget,
                      globalTarget,
                      name,
                      topicServiceName(name),
                      new DestinationServiceFactory() {
                          public DestinationService newService() {
                              return new DestroyableJMSTopicService(name, jndis);
                          }
                      },
                      new ValidatorFactory() {
                          @Override
                          public ReconfigurationValidator newValidator(DestinationService service) {
                              return new TopicReconfigurationValidator((DestroyableJMSTopicService)service,
                                                                       jndis);
                          }
                      },
                      waitForStart);
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
