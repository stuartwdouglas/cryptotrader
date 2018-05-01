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

    /**
     * This observes news events, and brodcasts them to all connected clients
     *
     * @param news the news event
     */
    public void news(@ObservesAsync @BitcoinNews String news) {
        broadcaster.broadcast(sse.newEvent(news));
    }

    @Produces(MediaType.SERVER_SENT_EVENTS)
    @GET
    public void watch(@Context SseEventSink sink) {
        broadcaster.register(sink);
    }

}
