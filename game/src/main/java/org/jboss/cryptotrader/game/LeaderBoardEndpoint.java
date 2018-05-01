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
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletionStage;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseBroadcaster;
import javax.ws.rs.sse.SseEventSink;

import org.jboss.cryptotrader.bank.AccountManager;

/**
 * Class that generates the leaderboard
 */
@Singleton
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
@Path("/leaderboard")
public class LeaderBoardEndpoint {


    private static final String NAME = "name";
    private static final String BANK_ACCOUNT_NO = "bankAccountNo";
    private static final String UNITS = "units";
    private static final String VALUE = "value";
    @Context
    private Sse sse;

    @Inject
    private AccountManager accountManager;

    /**
     * broadcaster used to notify clients of events
     */
    private SseBroadcaster broadcaster;

    /**
     * cached last leaderboard that we send on connect
     */
    private volatile String lastLeaderboard;

    @PostConstruct
    private void setup() {
        //set up the SSE broadcaster
        broadcaster = sse.newBroadcaster();
    }

    @PreDestroy
    private void close() {
        broadcaster.close();
    }

    @Schedule(second = "1,15,30,45", hour = "*", minute = "*")
    public void sendUpdate() {
        //we need to get the holdings and the price
        //we make two different requests and use the thenCombine method to
        //await both results
        CompletionStage<Response> holdings = ClientBuilder.newClient()
                .target(ExchangeService.BITCOIN_ALL_HOLDINGS)
                .request()
                .rx()
                .get();

        ClientBuilder.newClient()
                .target(ExchangeService.BITCOIN_PRICE)
                .request()
                .rx()
                .get()
                .thenAcceptBoth(holdings, (priceResponse, holdingsResponse) -> {
                    if (holdingsResponse.getStatus() != 200 || priceResponse.getStatus() != 200) {
                        return;
                    }
                    BigDecimal price = new BigDecimal(priceResponse.readEntity(String.class));
                    JsonArray data = holdingsResponse.readEntity(JsonArray.class);
                    List<User> users = new ArrayList<>();
                    for (JsonValue d : data) {
                        JsonObject o = d.asJsonObject();
                        BigDecimal holdings1 = o.getJsonNumber(UNITS).bigDecimalValue();
                        users.add(new User(o.getString(NAME), o.getString(BANK_ACCOUNT_NO), holdings1.multiply(price, MathContext.DECIMAL128)));
                    }
                    for (User user : users) {
                        try {
                            BigDecimal accountBalance = accountManager.getBalance(user.getBankAccountNo(), user.getName());
                            user.setNetWorth(user.getNetWorth().add(accountBalance));
                        } catch (Exception e) {
                            //ignore. It is possible if the bank was restarted after the exchange there may be some mismatches
                        }

                    }
                    users.sort(Comparator.reverseOrder());
                    JsonArrayBuilder result = Json.createArrayBuilder();
                    for (int i = 0; i < Math.min(5, users.size()); ++i) {
                        User user = users.get(i);
                        result.add(Json.createObjectBuilder()
                                .add(NAME, user.getName())
                                .add(VALUE, user.getNetWorth()));
                    }
                    broadcaster.broadcast(sse.newEvent(lastLeaderboard = result.build().toString()));

                });


    }

    @Produces(MediaType.SERVER_SENT_EVENTS)
    @GET
    public void watch(@Context SseEventSink sink) {
        broadcaster.register(sink);
        if(lastLeaderboard != null) {
            sink.send(sse.newEvent(lastLeaderboard));
        }
    }

    private class User implements Comparable<User> {
        private final String name;
        private final String bankAccountNo;
        private BigDecimal netWorth;

        public User(String name, String bankAccountNo, BigDecimal netWorth) {
            this.name = name;
            this.bankAccountNo = bankAccountNo;
            this.netWorth = netWorth;
        }

        public String getName() {
            return name;
        }

        public String getBankAccountNo() {
            return bankAccountNo;
        }

        public BigDecimal getNetWorth() {
            return netWorth;
        }

        public void setNetWorth(BigDecimal netWorth) {
            this.netWorth = netWorth;
        }

        @Override
        public int compareTo(User o) {
            return netWorth.compareTo(o.netWorth);
        }
    }

}
