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

package org.jboss.cryptotrader.bank;

import java.util.HashMap;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.ws.rs.GET;
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
@Path("/bank/balance")
@ApplicationScoped
public class BalanceEndpoint {

    @Context
    private Sse sse;

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
        //as we only want to watch one account number, we need to create a broadcaster per account
        //these broadcasters are stored in a map
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

    /**
     * Method that is notified when an account balance changes. If there is an SSE event broadcaster
     * registered
     *
     * @param event the bank transaction
     */
    public void transactionEvent(@Observes TransactionEvent event) {
        BroadcastHolder bc;
        synchronized (broadcasters) {
            bc = broadcasters.get(event.getAccount());
        }
        //notify any watchers of the account
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
