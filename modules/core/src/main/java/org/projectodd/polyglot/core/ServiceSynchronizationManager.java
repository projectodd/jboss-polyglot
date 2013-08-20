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

import org.jboss.logging.Logger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Transition;
import org.jboss.msc.service.ServiceListener;
import org.jboss.msc.service.ServiceName;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Provides synchronization on state change for MSC services. Acts as a layer on top of MSC for the
 * given services, since we need to be able to wait for state changes on MSC services that are in the
 * process of being registered with the MSC asynchronously.
 */
@SuppressWarnings("rawtypes")
public class ServiceSynchronizationManager implements ServiceListener {

    /**
     * The singleton manager.
     */
    public static final ServiceSynchronizationManager INSTANCE = new ServiceSynchronizationManager();

    public enum State {NEW, WAITING, UP, DOWN, REMOVED}

    public Service getService(ServiceName name) {
        if (hasService(name)) {
            return serviceData.get(name).service;
        }

        return null;
    }

    public void addService(ServiceName name, Service service) {
        addService(name, service, false);
    }

    public void addService(ServiceName name, Service service, boolean replace) {
        log.debug("addService: " + name + " " + service);
        if (!replace &&
                hasService(name)) {
            throw new IllegalStateException(name + " already registered");
        }

        synchronized (this) {
            ServiceData oldValue = serviceData.get(name);
            if (oldValue != null) {
                servicesToNames.remove(oldValue.service);
            }
            serviceData.put(name, new ServiceData(name, service));
            servicesToNames.put(service, name);
        }
    }

    public void addService(ServiceName name, Service service, ServiceName dependency) {
        log.debug("addService: " + name + " " + service + " " + dependency);

        synchronized (this) {
            addService(name, service);
            if (hasService(dependency)) {
                lookup(dependency).addDependent(service);
                log.debug("addService: added dependent to " + dependency + " " + lookup(dependency).dependents);
            }
        }
    }

    public void setServiceState(Service service, State state) {
        synchronized (this) {
            ServiceData data = lookup(service);
            if (data != null) {
                log.debug("setServiceState: setting state for " + service + " from " + data.state + " to " + state);

                data.state = state;

                data.tripLatch();
            }
        }
    }

    public boolean hasDependents(Service service) {
        return hasService(service) &&
                lookup(service).dependents.size() > 0;
    }

    public boolean hasDependents(ServiceName serviceName) {
        return hasService(serviceName) &&
                lookup(serviceName).dependents.size() > 0;
    }

    private boolean waitForServiceInState(ServiceName name, Set<State> states, long timeout) {
        ServiceData data;
        synchronized (this) {
            data = lookup(name);

            log.debug("waitForServiceInState: " + name + ", states: " + states + ", current state: " + data.state);
            if (states.contains(data.state)) {
                return true;
            }

            data.resetLatch();
        }

        return data.await(timeout) && waitForServiceInState(name, states, timeout);
    }

    public boolean waitForServiceStart(ServiceName name, long timeout) {
        return waitForServiceInState(name,
                                     new HashSet<State>() {{
                                         add(State.WAITING);
                                         add(State.UP);
                                     }},
                                     timeout);
    }


    public boolean waitForServiceRemove(ServiceName name, long timeout) {
        return waitForServiceInState(name,
                                     new HashSet<State>() {{
                                         add(State.REMOVED);
                                     }},
                                     timeout);
    }

    public boolean waitForServiceDown(ServiceName name, long timeout) {
        return waitForServiceInState(name,
                                     new HashSet<State>() {{
                                         add(State.DOWN);
                                         add(State.WAITING);
                                         add(State.REMOVED);
                                     }},
                                     timeout);
    }

    protected void removeDependents(Service service) {
        synchronized (this) {
            for (ServiceData each : this.serviceData.values()) {
                each.removeDependent(service);
            }
        }
    }

    protected void removeService(Service service) {
        synchronized (this) {
            removeDependents(service);
            setServiceState(service, State.REMOVED);
        }
    }

    public boolean hasService(Service service) {
        ServiceName name = this.servicesToNames.get(service);
        return (name != null &&
                hasService(name));
    }

    public boolean hasService(ServiceName name) {
        ServiceData data = this.serviceData.get(name);
        return (data != null &&
                data.state != State.REMOVED);
    }

    private ServiceData lookup(ServiceName name) {
        if (name == null) {
            return null;
        }

        return serviceData.get(name);
    }

    private ServiceData lookup(Service service) {
        return lookup(servicesToNames.get(service));
    }

    @Override
    public void transition(ServiceController controller,
                           Transition transition) {
        Service service = controller.getService();
        log.trace(transition + " for " + controller);
        if (hasService(service)) {
            switch (transition) {
                case DOWN_to_WAITING:
                    log.debug("moving " + controller.getName() + " to WAITING");
                    setServiceState(service, State.WAITING);
                    break;
                case STARTING_to_UP:
                case STOP_REQUESTED_to_UP:
                    log.debug("moving " + controller.getName() + " to UP");
                    setServiceState(service, State.UP);
                    break;
                case START_REQUESTED_to_DOWN:
                case START_FAILED_to_DOWN:
                case STOPPING_to_DOWN:
                case REMOVING_to_DOWN:
                case WAITING_to_DOWN:
                case WONT_START_to_DOWN:
                    log.debug("moving " + controller.getName() + " to DOWN");
                    setServiceState(service, State.DOWN);
                    break;
                case REMOVING_to_REMOVED:
                    log.debug("moving " + controller.getName() + " to REMOVED");
                    removeService(service);
                    break;
            }
        }
    }

    @Override
    public void listenerAdded(ServiceController controller) {
    }


    @Override
    public void serviceRemoveRequested(ServiceController controller) {
    }

    @Override
    public void serviceRemoveRequestCleared(ServiceController controller) {
    }

    @Override
    public void dependencyFailed(ServiceController controller) {
    }

    @Override
    public void dependencyFailureCleared(ServiceController controller) {
    }

    @Override
    public void immediateDependencyUnavailable(ServiceController controller) {
    }

    @Override
    public void immediateDependencyAvailable(ServiceController controller) {
    }

    @Override
    public void transitiveDependencyUnavailable(ServiceController controller) {
    }

    @Override
    public void transitiveDependencyAvailable(ServiceController controller) {
    }

    static final Logger log = Logger.getLogger("org.projectodd.polyglot.core.ServiceSynchronizationManager");

    private Map<ServiceName, ServiceData> serviceData = new ConcurrentHashMap<ServiceName, ServiceData>();
    private Map<Service, ServiceName> servicesToNames = new ConcurrentHashMap<Service, ServiceName>();

    class ServiceData {

        public ServiceData(ServiceName name, Service service) {
            this.name = name;
            this.service = service;
        }

        public void addDependent(Service dependent) {
            this.dependents.add(dependent);
        }

        public boolean removeDependent(Service dependent) {
            if (this.dependents.contains(dependent)) {
                this.dependents.remove(dependent);

                return true;
            }

            return false;
        }

        public boolean await(long timeout) {
            boolean ret = false;
            try {
                ret = this.latch.await(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
            }

            return ret;
        }

        public void resetLatch() {
            log.debug("resetLatch for " + this.name);
            this.latch = new CountDownLatch(1);
        }

        public boolean tripLatch() {
            log.debug("tripLatch for " + this.name);
            if (this.latch != null) {
                this.latch.countDown();

                return true;
            }

            return false;
        }

        ServiceName name;
        Service service;
        State state = State.NEW;
        Set<Service> dependents = new HashSet<Service>();
        CountDownLatch latch;
    }
}
