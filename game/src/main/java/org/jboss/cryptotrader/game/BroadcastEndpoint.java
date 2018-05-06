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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

@Path("/broadcast")
@ApplicationScoped
public class BroadcastEndpoint {

    /**
     * broadcaster used to notify clients of events
     */
    private SseBroadcaster broadcaster;

    @Context
    private Sse sse;

    private final Map<String, BroadcastEvent> mostRecent = new ConcurrentHashMap<>();


    private void event(@ObservesAsync BroadcastEvent event) {
        broadcaster.broadcast(sse.newEvent(event.getName(), event.getData()));
        mostRecent.put(event.getName(), event);
    }


    @PostConstruct
    private void setup() {
        //set up the SSE broadcaster
        broadcaster = sse.newBroadcaster();
    }

    @PreDestroy
    private void close() {
        broadcaster.close();
    }

    @Produces(MediaType.SERVER_SENT_EVENTS)
    @GET
    public void watch(@Context SseEventSink sink) {
        broadcaster.register(sink);
        for (Map.Entry<String, BroadcastEvent> e : mostRecent.entrySet()) {
            sink.send(sse.newEvent(e.getKey(), e.getValue().getData()));
        }
    }
}
