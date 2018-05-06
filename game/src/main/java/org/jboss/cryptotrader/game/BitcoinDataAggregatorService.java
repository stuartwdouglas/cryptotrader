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
import javax.ejb.Startup;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonArrayBuilder;

/**
 * An endpoint that aggregates data from the exchange, and sends this aggregated data to the client at
 * two second intervals.
 * <p>
 * This uses the new JAX-RS server sent event API in order to listener for events that are generated from the exchange
 * <p>
 * This aggregates two types of data, price changes and news messages
 */
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
@Startup
@Singleton
public class BitcoinDataAggregatorService {

    /**
     * factory that abstracts away the details of using the SSE client
     */
    @Inject
    private PersistentSseClientFactory persistentSseClientFactory;

    /**
     * Used to trigger events at two second intervals
     */
    @Resource
    private ManagedScheduledExecutorService scheduledExecutorService;

    /**
     * The current price
     */
    private volatile BigDecimal bitcoinPrice = BigDecimal.ZERO;

    /**
     * Messages to be sent out, access must be synchronized
     */
    private final List<String> newsMessages = new ArrayList<>();

    /**
     * handle that is used to stop the timer on undeploy
     */
    private volatile ScheduledFuture<?> timerHandler;

    @Inject
    private Event<BroadcastEvent> broadcast;


    @PostConstruct
    private void setup() {
        //set up the SSE broadcaster
        timerHandler = scheduledExecutorService.scheduleAtFixedRate(this::sendMessages, 4, 2, TimeUnit.SECONDS);

        //sign up for price notifications
        persistentSseClientFactory.createPersistentConnection(inboundSseEvent -> {
            bitcoinPrice = new BigDecimal(inboundSseEvent.readData());
        }, ExchangeService.BITCOIN_PRICE_WATCH);

        //sign up for news notifications
        persistentSseClientFactory.createPersistentConnection(inboundSseEvent -> {
            synchronized (newsMessages) {
                newsMessages.add(inboundSseEvent.readData());
            }
        }, ExchangeService.BITCOIN_NEWS);


    }

    @PreDestroy
    private void tearDown() {
        timerHandler.cancel(true);
    }

    /**
     * This method is called every two seconds to send out aggregate updates to clients
     */
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
        broadcast.fireAsync(new BroadcastEvent("bitcoin", message));
    }

}
