/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.cryptotrader.game;


import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.sse.InboundSseEvent;
import javax.ws.rs.sse.SseEventSource;

/**
 * Manager classs that deals with SSE clients, and handles automatic reconnect in the event of error.
 */
@ApplicationScoped
public class PersistentSseClientFactory {

    @Resource
    private ManagedScheduledExecutorService scheduledExecutorService;

    /**
     * The connection list. This is maintained so that they can be correctly closed on undeploy
     */
    private final List<Connection> connections = new CopyOnWriteArrayList<>();

    private Client client;


    @PostConstruct
    private void setup() {
        client = ClientBuilder.newClient();
    }

    /**
     * close all clients when the application is undeployed
     */
    @PreDestroy
    void stop() {
        for(Connection c : new ArrayList<>(connections)) {
            c.close();
        }
        client.close();
    }

    /**
     * Creates a new SSE client. This client will autoamtically reconnect if there is a problem
     * @param messageHandler The message handler
     * @param target The URI to connect to
     * @return A closeable that can be used to close the client
     */
    public Closeable createPersistentConnection(Consumer<InboundSseEvent> messageHandler, String target) {
        Connection c = new Connection(messageHandler, target);
        connections.add(c);
        c.doConnect();
        return c;
    }

    /**
     * A persistent SSE connection
     */
    private final class Connection implements Closeable {

        private final Consumer<InboundSseEvent> messageHandler;
        private final String target;

        private boolean closed;
        private SseEventSource source;
        private boolean reconnectScheduled = false;
        private ScheduledFuture<?> handle;

        Connection(Consumer<InboundSseEvent> messageHandler, String target) {
            this.messageHandler = messageHandler;
            this.target = target;
        }

        synchronized void doConnect() {
            if (closed) {
                return;
            }
            handle = null;
            reconnectScheduled = false;

            //create the SSE connection object
            source = SseEventSource.target(client.
                    target(target)).build();
            //register the message and error handlers
            source.register(messageHandler, throwable -> {
                try {
                    source.close();
                } finally {
                    attemptReconnect();
                }
            }, this::attemptReconnect);
            source.open(); //open the connection
        }

        /**
         * If the connection fails we wait a few seconds then try again
         */
        synchronized void attemptReconnect() {
            if (closed || reconnectScheduled) {
                return;
            }
            reconnectScheduled = true;
            handle = scheduledExecutorService.schedule(this::doConnect, 2, TimeUnit.SECONDS);
        }

        /**
         * close the client
         */
        @Override
        public synchronized void close() {
            closed = true;
            if (source != null) {
                source.close();
            }
            if (handle != null) {
                handle.cancel(false);
                handle = null;
            }
            connections.remove(this);
        }
    }
}
