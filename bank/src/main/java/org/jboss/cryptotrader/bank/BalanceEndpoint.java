package org.jboss.cryptotrader.bank;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseBroadcaster;
import javax.ws.rs.sse.SseEventSink;

/**
 * Endpoint that can be used to query the bank balance, and register for server sent events to be notified of changes
 */
@Path("/balance")
@ApplicationScoped
public class BalanceEndpoint {

    @Context
    private Sse sse;

    @Inject
    private AccountManager manager;

    private final Map<String, BroadcastHolder> broadcasters = new HashMap<>();

    /**
     * SSE endpoint for receiving notification about balance changes
     * <p>
     * This registers a SseEventSink with a broadcaster, the broadcasters are stored in a map keyed
     * by account number. We have to maintain this map ourselves.
     *
     * @param sink      The sink
     * @param accountNo The account number
     */
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Path("/watch/{accountNo}")
    @GET
    public void watch(@Context SseEventSink sink, @PathParam("accountNo") String accountNo) {
        synchronized (broadcasters) {
            BroadcastHolder bc = broadcasters.get(accountNo);
            if (bc == null) {
                final BroadcastHolder holder = bc = new BroadcastHolder(sse.newBroadcaster());
                broadcasters.put(accountNo, bc);
                bc.broadcaster.onClose((c) -> {
                    if (--holder.usageCount == 0) {
                        broadcasters.remove(accountNo);
                    }
                });
            } else {
                bc.usageCount++;
            }
            bc.broadcaster.register(sink);

        }
    }

    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{accountNo}")
    @POST
    public BigDecimal accountDetails(@PathParam("accountNo") String accountNo, JsonObject object) {
        String client = object.getString("name");
        BigDecimal balance = manager.getBalance(accountNo, client);
        return (BigDecimal) Json.createObjectBuilder()
                .add("accountNo", accountNo)
                .add("name", client)
                .add("balance", balance)
                .build();
    }

    /**
     * Method that is notified when an account balance changes. If there is an SSE event broadcaster
     * registered
     *
     * @param event
     */
    public void transactionEvent(@Observes TransactionEvent event) {
        BroadcastHolder bc;
        synchronized (broadcasters) {
            bc = broadcasters.get(event.getAccount());
        }
        if (bc != null) {
            bc.broadcaster.broadcast(sse.newEvent("balance", event.getBalance().toString()));
        }
    }

    /**
     * class to track usage counts, so map entries can be removed
     */
    private final class BroadcastHolder {

        final SseBroadcaster broadcaster;
        int usageCount = 1;

        private BroadcastHolder(SseBroadcaster broadcaster) {
            this.broadcaster = broadcaster;
        }
    }

}
