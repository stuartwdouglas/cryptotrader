package org.jboss.cryptotrader.game;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.Singleton;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseBroadcaster;
import javax.ws.rs.sse.SseEventSink;

import org.jboss.cryptotrader.ExchangeService;

@Path("/bitcoin/data")
@Singleton
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
public class BitcoinDataAggregatorEndpoint {

    @Context
    private Sse sse;

    @Inject
    private PersistentSseClient persistentSseClient;

    @Resource
    private ManagedScheduledExecutorService scheduledExecutorService;

    private SseBroadcaster broadcaster;

    private volatile BigDecimal bitcoinPrice = BigDecimal.ZERO;
    private final List<String> newsMessages = new ArrayList<>();
    private volatile ScheduledFuture<?> timerHandler;


    @PostConstruct
    private void setup() {
        //set up the SSE broadcaster
        broadcaster = sse.newBroadcaster();
        timerHandler = scheduledExecutorService.scheduleAtFixedRate(this::sendMessages, 4, 2, TimeUnit.SECONDS);

        //sign up for price notifications
        persistentSseClient.createPersistentConnection(inboundSseEvent -> {
            bitcoinPrice = new BigDecimal(inboundSseEvent.readData());
        }, ExchangeService.BITCOIN_WATCH);

        //sign up for news notifications
        persistentSseClient.createPersistentConnection(inboundSseEvent -> {
            synchronized (BitcoinDataAggregatorEndpoint.this) {
                newsMessages.add(inboundSseEvent.readData());
            }
        }, ExchangeService.BITCOIN_NEWS);


    }

    @PreDestroy
    private void tearDown() {
        timerHandler.cancel(true);
        broadcaster.close();
    }

    //send out price updates every two seconds
    public void sendMessages() {
        JsonArrayBuilder newsArray = Json.createArrayBuilder();
        synchronized (newsMessages) {
            newsMessages.forEach(newsArray::add);
            newsMessages.clear();
        }
        String message = Json.createObjectBuilder()
                .add("bitcoin", bitcoinPrice)
                .add("news", newsArray)
                .build()
                .toString();
        broadcaster.broadcast(sse.newEvent(message));
    }

    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Path("/watch")
    @GET
    public void watch(@Context SseEventSink sink) {
        broadcaster.register(sink);
    }

}
