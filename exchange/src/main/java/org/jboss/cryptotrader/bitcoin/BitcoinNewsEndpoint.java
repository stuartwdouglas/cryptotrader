package org.jboss.cryptotrader.bitcoin;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.ObservesAsync;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseBroadcaster;
import javax.ws.rs.sse.SseEventSink;

/**
 * Endpoint that publishes bitcoin news using server sent events
 */
@Path("/bitcoin/news")
@ApplicationScoped
public class BitcoinNewsEndpoint {

    @Context
    private Sse sse;

    private SseBroadcaster broadcaster;

    @PostConstruct
    private void setup() {
        broadcaster = sse.newBroadcaster();
    }

    @PreDestroy
    private void tearDown() {
        broadcaster.close();
    }

    public void news(@ObservesAsync @BitcoinNews String news) {
        broadcaster.broadcast(sse.newEvent(news));
    }

    @Produces(MediaType.SERVER_SENT_EVENTS)
    @GET
    public void watch(@Context SseEventSink sink) {
        broadcaster.register(sink);
    }

}
