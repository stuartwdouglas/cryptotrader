package org.jboss.cryptotrader.game;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;
import javax.ws.rs.sse.SseEventSource;

/**
 * Most of the methods on this endpoint simply forward to the bank service using the JAX-RS client.
 * <p>
 * This endpoint uses JSONB to bind the bank account details directly to JSON
 */
@Path("/bank")
public class BankEndpoint {

    @Path("/open")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    public BankAccount openAccount(BankAccount clientDetails) {
        return ClientBuilder.newClient().target(BankServices.open())
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.entity(clientDetails, MediaType.APPLICATION_JSON_TYPE), BankAccount.class);
    }

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
    public void watch(@Context SseEventSink sink, @Context Sse sse, @PathParam("accountNo") String accountNo) {
        SseEventSource source = SseEventSource.target(ClientBuilder.newClient().target(BankServices.watch(accountNo))).build();
        source.register(event -> {
            sink.send(sse.newEvent(event.getName(), event.readData()));
        }, error -> {
            sink.close();
            source.close();
        });
    }

    /**
     * Returns the bank balance
     *
     * @param accountNo  The account number
     * @param clientName The client name
     */
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/balance/{accountNo}/{name}")
    @GET
    public BankAccount balance(@PathParam("accountNo") String accountNo, @PathParam("name") String clientName) {
        BankAccount account = new BankAccount();
        account.setAccountNo(accountNo);
        account.setName(clientName);
        return ClientBuilder.newClient().target(BankServices.balance(accountNo))
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.entity(account, MediaType.APPLICATION_JSON_TYPE), BankAccount.class);

    }


}
