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

package org.projectodd.polyglot.hasingleton;

import java.util.List;

import org.jboss.as.clustering.ClusterNode;
import org.jboss.as.clustering.GroupMembershipListener;
import org.jboss.as.clustering.impl.CoreGroupCommunicationService;
import org.jboss.as.clustering.jgroups.ChannelFactory;
import org.jboss.logging.Logger;
import org.jboss.modules.ModuleLoader;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.value.ImmediateValue;
import org.jboss.msc.value.Value;
import org.jgroups.Channel;

public class HASingletonCoordinator implements GroupMembershipListener {
    
	
	public HASingletonCoordinator(ServiceController<Void> haSingletonController, Value<Channel> channelRef, Value<ModuleLoader> moduleLoaderRef) {
        this.haSingletonController = haSingletonController;
        this.channelRef = channelRef;
        this.moduleLoaderRef = moduleLoaderRef;
    }
    
    public void start() throws Exception {
        log.info( "Connect to " + this.partitionName );
        this.service = new CoreGroupCommunicationService( SCOPE_ID, channelRef, moduleLoaderRef );
        this.service.setAllowSynchronousMembershipNotifications( true );
        //this.service.setChannelFactory( this.channelFactory );
        this.service.registerGroupMembershipListener( this );
        //this.service.setChannelStackName( "jgroups-udp" );
        //this.service.setGroupName( this.partitionName );
        this.service.start();
    }
    
    public void stop() throws Exception {
        this.service.stop();
    }
    
    protected boolean shouldBeMaster(List<ClusterNode> members) {
        log.info( "inquire if we should be master" );
        if ( members.isEmpty() ) {
            return false;
        }
        
        ClusterNode coordinator = members.get( 0 );
        
        return this.service.getClusterNode().equals( coordinator );
    }
    
    @Override
    public void membershipChanged(List<ClusterNode> deadMembers, List<ClusterNode> newMembers, List<ClusterNode> allMembers) {
        if ( shouldBeMaster( allMembers ) ) {
            log.info( "Becoming HASingleton master." );
            haSingletonController.setMode( Mode.ACTIVE );
        } else {
            log.info( "Ensuring NOT HASingleton master." );
            haSingletonController.setMode( Mode.NEVER );
        }
    }

    @Override
    public void membershipChangedDuringMerge(List<ClusterNode> deadMembers, List<ClusterNode> newMembers, List<ClusterNode> allMembers, List<List<ClusterNode>> originatingGroups) {
        membershipChanged( deadMembers, newMembers, allMembers );
    }

    
    private static final Logger log = Logger.getLogger( "org.projectodd.polyglot.hasingleton" );
    
    private Value<Channel> channelRef;
	private Value<ModuleLoader> moduleLoaderRef;
    private CoreGroupCommunicationService service;
    private ServiceController<Void> haSingletonController;
    private String partitionName;
    public static final short SCOPE_ID = 248; // Must be different from any scopes AS7 uses internally
}
