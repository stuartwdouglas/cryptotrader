package org.jboss.cryptotrader.game;

import java.math.BigDecimal;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.Singleton;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.json.Json;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseBroadcaster;
import javax.ws.rs.sse.SseEventSink;
import javax.ws.rs.sse.SseEventSource;

import org.jboss.cryptotrader.ExchangeService;

@Path("/price")
@Singleton
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
public class PriceAggregatorEndpoint {

    @Context
    private Sse sse;

    private SseBroadcaster broadcaster;

    private volatile BigDecimal bitcoinPrice = BigDecimal.ZERO;

    @Resource
    private ManagedScheduledExecutorService scheduledExecutorService;
    private volatile SseEventSource source;
    private volatile ScheduledFuture<?> timerHandler;
    private volatile boolean closed;
    private boolean reconnectScheduled;

    @PostConstruct
    private void setup() {
        //set up the SSE broadcaster
        broadcaster = sse.newBroadcaster();
        attemptBitcoinReconnect();
    }

    private void doBitcoinConnect() {
        synchronized (this) {
            reconnectScheduled = false;
        }
        source = SseEventSource.target(ClientBuilder.newClient().
                target(ExchangeService.BITCOIN_WATCH)).build();
        timerHandler = scheduledExecutorService.scheduleAtFixedRate(this::sendMessages, 2, 2, TimeUnit.SECONDS);
        source.register(inboundSseEvent -> {
            bitcoinPrice = new BigDecimal(inboundSseEvent.readData());
        }, throwable -> {
            try {
                source.close();
            } finally {
                attemptBitcoinReconnect();
            }
        }, this::attemptBitcoinReconnect);
        source.open();
    }

    private synchronized void attemptBitcoinReconnect() {
        if (closed || reconnectScheduled) return;
        reconnectScheduled = true;
        scheduledExecutorService.schedule(this::doBitcoinConnect, 2, TimeUnit.SECONDS);
    }

    @PreDestroy
    private void tearDown() {
        closed = true;
        timerHandler.cancel(true);
        broadcaster.close();
        if (source != null) {
            source.close();
            source = null;
        }
    }

    //send out price updates every two seconds
    public void sendMessages() {
        String message = Json.createObjectBuilder()
                .add("bitcoin", bitcoinPrice)
                .build().toString();
        broadcaster.broadcast(sse.newEvent(message));
    }

    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Path("/watch")
    @GET
    public void watch(@Context SseEventSink sink) {
        broadcaster.register(sink);
    }

}
