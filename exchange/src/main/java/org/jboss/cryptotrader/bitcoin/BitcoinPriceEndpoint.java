package org.jboss.cryptotrader.bitcoin;

import java.math.BigDecimal;
import java.math.RoundingMode;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.event.ObservesAsync;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseBroadcaster;
import javax.ws.rs.sse.SseEventSink;

/**
 * Endpoint that can be used to get bitcoin price
 */
@Path("/bitcoin/price")
@ApplicationScoped
public class BitcoinPriceEndpoint {

    @Inject
    private BitcoinPriceService priceService;

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

    @Produces(MediaType.TEXT_PLAIN)
    @Path("/")
    @GET
    public BigDecimal price() {
        return priceService.getPrice();
    }

    public void priceChange(@Observes @BitcoinPriceChange BigDecimal price) {
        broadcaster.broadcast(sse.newEvent(price.setScale(10, RoundingMode.HALF_DOWN).toString()));
    }

    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Path("/watch")
    @GET
    public void watch(@Context SseEventSink sink) {
        broadcaster.register(sink);
    }

}
