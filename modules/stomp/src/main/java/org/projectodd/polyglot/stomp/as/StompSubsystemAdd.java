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

package org.projectodd.polyglot.stomp.as;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.*;
import static org.projectodd.polyglot.core.processors.RootedDeploymentProcessor.*;

import java.util.List;

import javax.net.ssl.SSLContext;
import javax.transaction.TransactionManager;

import org.apache.catalina.connector.Connector;
import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ServiceVerificationHandler;
import org.jboss.as.network.SocketBinding;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.as.server.deployment.Phase;
import org.jboss.as.txn.service.TxnServices;
//import org.jboss.as.web.WebSubsystemServices;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.projectodd.polyglot.stomp.InsecureStompConnectorService;
import org.projectodd.polyglot.stomp.SSLContextService;
import org.projectodd.polyglot.stomp.SecureStompConnectorService;
import org.projectodd.polyglot.stomp.StompWebAdjuster;
import org.projectodd.polyglot.stomp.StompletServerService;
import org.projectodd.polyglot.stomp.processors.SessionManagerInstaller;
import org.projectodd.polyglot.stomp.processors.StompletContainerInstaller;
import org.projectodd.stilts.stomplet.server.StompletServer;

public class StompSubsystemAdd extends AbstractBoottimeAddStepHandler {

    @Override
    protected void populateModel(ModelNode operation, ModelNode subModel) {
        subModel.get( "socket-binding" ).set( operation.get( "socket-binding" ) );
        if (operation.has( "secure-socket-binding" )) {
            subModel.get( "secure-socket-binding" ).set( operation.get( "secure-socket-binding" ) );
        }
    }

    @Override
    protected void performBoottime(OperationContext context, final ModelNode operation, ModelNode model, ServiceVerificationHandler verificationHandler,
            List<ServiceController<?>> newControllers) throws OperationFailedException {

        context.addStep( new AbstractDeploymentChainStep() {
            @Override
            protected void execute(DeploymentProcessorTarget processorTarget) {
                String bindingRef = operation.require( "socket-binding" ).asString();
                String secureBindingRef = null;
                if (operation.has( "secure-socket-binding" )) {
                    secureBindingRef = operation.require( "secure-socket-binding" ).asString();
                }
                addDeploymentProcessors( processorTarget, bindingRef, secureBindingRef );
            }
        }, OperationContext.Stage.RUNTIME );

        try {
            addCoreServices( context, operation, model, verificationHandler, newControllers );
        } catch (Exception e) {
            throw new OperationFailedException( e.getMessage(), e );
        }
    }

    protected void addCoreServices(OperationContext context, ModelNode operation, ModelNode model, ServiceVerificationHandler verificationHandler,
            List<ServiceController<?>> newControllers) {
        addSSLContextService( context, operation, model, verificationHandler, newControllers );
        addStompletServer( context, operation, model, verificationHandler, newControllers );
        addInsecureConnector( context, operation, model, verificationHandler, newControllers );
        addSecureConnector( context, operation, model, verificationHandler, newControllers );
    }

    private void addSSLContextService(OperationContext context, ModelNode operation, ModelNode model, ServiceVerificationHandler verificationHandler,
            List<ServiceController<?>> newControllers) {

        SSLContextService service = new SSLContextService();

//        ServiceController<SSLContext> controller = context.getServiceTarget().addService( StompServices.SSL_CONTEXT, service )
//                .addDependency( WebSubsystemServices.JBOSS_WEB_CONNECTOR.append( "https" ), Connector.class, service.getWebConnectorInjector() )
//                .setInitialMode( Mode.PASSIVE )
//                .addListener( verificationHandler )
//                .install();
//
//        newControllers.add( controller );

    }

    private void addStompletServer(OperationContext context, ModelNode operation, ModelNode model, ServiceVerificationHandler verificationHandler,
            List<ServiceController<?>> newControllers) {
        StompletServerService service = new StompletServerService();

        ServiceController<StompletServer> controller = context.getServiceTarget().addService( StompServices.SERVER, service )
                .addDependency( TxnServices.JBOSS_TXN_TRANSACTION_MANAGER, TransactionManager.class, service.getTransactionManagerInjector() )
                .setInitialMode( Mode.ON_DEMAND )
                .addListener( verificationHandler )
                .install();

        newControllers.add( controller );
    }

    private void addInsecureConnector(OperationContext context, ModelNode operation, ModelNode model, ServiceVerificationHandler verificationHandler,
            List<ServiceController<?>> newControllers) {
        final String bindingRef = operation.require( "socket-binding" ).asString();

        InsecureStompConnectorService service = new InsecureStompConnectorService();
        ServiceController<org.projectodd.stilts.stomp.server.Connector> controller = context.getServiceTarget()
                .addService( StompServices.CONNECTOR.append( "insecure" ), service )
                .addDependency( SocketBinding.JBOSS_BINDING_NAME.append( bindingRef ), SocketBinding.class, service.getSocketBindingInjector() )
                .addDependency( StompServices.SERVER, StompletServer.class, service.getStompletServerInjector() )
                .setInitialMode( Mode.PASSIVE )
                .addListener( verificationHandler )
                .install();

        newControllers.add( controller );
    }

    private void addSecureConnector(OperationContext context, ModelNode operation, ModelNode model, ServiceVerificationHandler verificationHandler,
            List<ServiceController<?>> newControllers) {
        final String bindingRef = operation.get( "secure-socket-binding" ).asString();

        if (!operation.has( "secure-socket-binding" )) {
            return;
        }

        ServiceController<?> bindingService = context.getServiceRegistry( true ).getService( SocketBinding.JBOSS_BINDING_NAME.append( bindingRef ) );
        if (bindingService != null) {
            bindingService.setMode( Mode.ACTIVE );
        }

        SecureStompConnectorService service = new SecureStompConnectorService();
        ServiceController<org.projectodd.stilts.stomp.server.Connector> controller = context.getServiceTarget()
                .addService( StompServices.CONNECTOR.append( "secure" ), service )
                .addDependency( SocketBinding.JBOSS_BINDING_NAME.append( bindingRef ), SocketBinding.class, service.getSocketBindingInjector() )
                .addDependency( StompServices.SERVER, StompletServer.class, service.getStompletServerInjector() )
                .addDependency( StompServices.SSL_CONTEXT, SSLContext.class, service.getSSLContextInjector() )
                .setInitialMode( Mode.PASSIVE )
                .addListener( verificationHandler )
                .install();

        newControllers.add( controller );
    }

    protected void addDeploymentProcessors(final DeploymentProcessorTarget processorTarget, String socketBindingRef, String secureSocketBindingRef) {
        processorTarget.addDeploymentProcessor( StompExtension.SUBSYSTEM_NAME, Phase.PARSE, 1031, rootSafe( new StompWebAdjuster() ) );
        processorTarget.addDeploymentProcessor( StompExtension.SUBSYSTEM_NAME, Phase.DEPENDENCIES, 5, rootSafe( new StompDependenciesProcessor() ) );
        processorTarget.addDeploymentProcessor( StompExtension.SUBSYSTEM_NAME, Phase.INSTALL, 99, rootSafe( new SessionManagerInstaller( "localhost" ) ) );
        processorTarget.addDeploymentProcessor( StompExtension.SUBSYSTEM_NAME, Phase.INSTALL, 100, rootSafe( new StompletContainerInstaller( socketBindingRef,
                secureSocketBindingRef ) ) );
    }

    static ModelNode createOperation(ModelNode address) {
        final ModelNode subsystem = new ModelNode();
        subsystem.get( OP ).set( ADD );
        subsystem.get( OP_ADDR ).set( address );
        return subsystem;
    }

    static final StompSubsystemAdd ADD_INSTANCE = new StompSubsystemAdd();
    static final Logger log = Logger.getLogger( "org.projectodd.polyglot.stomp.as" );

}
