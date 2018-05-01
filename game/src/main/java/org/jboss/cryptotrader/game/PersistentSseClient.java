package org.jboss.cryptotrader.game;


import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.sse.InboundSseEvent;
import javax.ws.rs.sse.SseEventSource;

@ApplicationScoped
public class PersistentSseClient {

    @Resource
    private ManagedScheduledExecutorService scheduledExecutorService;

    private final List<Connection> connections = new CopyOnWriteArrayList<>();

    public Closeable createPersistentConnection(Consumer<InboundSseEvent> messageHandler, String target) {
        Connection c = new Connection(messageHandler, target);
        connections.add(c);
        c.doConnect();
        return c;
    }

    @PreDestroy
    void stop() {
        for(Connection c : new ArrayList<>(connections)) {
            c.close();
        }
    }

    private final class Connection implements Closeable {

        private final Consumer<InboundSseEvent> messageHandler;
        private final String target;

        private volatile boolean closed;
        private volatile SseEventSource source;
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
            source = SseEventSource.target(ClientBuilder.newClient().
                    target(target)).build();
            source.register(messageHandler, throwable -> {
                try {
                    source.close();
                } finally {
                    attemptReconnect();
                }
            }, this::attemptReconnect);
            source.open();
        }

        synchronized void attemptReconnect() {
            if (closed || reconnectScheduled) {
                return;
            }
            reconnectScheduled = true;
            handle = scheduledExecutorService.schedule(this::doConnect, 2, TimeUnit.SECONDS);
        }

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
