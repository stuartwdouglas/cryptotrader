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

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.swing.text.NumberFormatter;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * The bitcoin exchange
 *
 * This simulates actually trading.
 *
 * Holdings are stored in a map keyed by name+account number.
 *
 */
@Path("/bitcoin/trade")
@ApplicationScoped
public class BitcoinExchangeEndpoint {

    private static final String TRANSACT;

    private static final String SERVICE_NAME = System.getProperty("cryptotrader.game", "game");

    /**
     * The URI we use to connect to the bank varies depending on if we are running in openshift or not.
     *
     * In a real app these should probably not be hard coded
     */
    static {
        String host;
        try {
            InetAddress address = InetAddress.getByName(SERVICE_NAME);
            System.out.println("BitcoinExchangeEndpoint: Using openshift services, resolved IP: " + address);
            host = "http://" + SERVICE_NAME + ":8080/game/rest/bank/transact/";
        } catch (UnknownHostException e) {
            host = "http://localhost:8080/game/rest/bank/transact/";
        }
        TRANSACT = host;

    }
    /**
     * We just track holdings in a map keyed by client name. Operations on this map must be synchronised.
     */
    private final Map<UserKey, BigDecimal> holdings = new HashMap<>();

    @Inject
    private BitcoinPriceService priceService;

    /**
     * We simulate a delay in trades going though by scheduling tasks for later execution
     */
    @Resource
    private ManagedScheduledExecutorService managedScheduledExecutorService;

    /**
     * When a transaction completes we publish the details to the news stream
     */
    @Inject
    @BitcoinNews
    private Event<String> newsEvent;

    private Client client;

    @PostConstruct
    private void setup() {
        client = ClientBuilder.newClient();
    }

    @PreDestroy
    private void close() {
        client.close();
    }

    /**
     * The trading endpoint. Trades are performed asyncronously, but slightly differently for purchaes and sales
     *
     * Purchases take the money from the bank immediately, then have a delay while the trade goes through, then return a result to the client
     * Sales perform the trade immediately and return a result, however the money does not go through to the bank for some time
     *
     * In both cases the end user is notified of changes to the bank balance by the bank itself, the trade endpoint does not
     * return any information about bank balance changes.
     *
     * @param response This uses JAX-RS async invocations, so this is the async response used to send the response to the client
     * @param trade The incoming trade, mapped using the new JSONB spec
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON})
    public void trade(@Suspended AsyncResponse response,  BitcoinTradeData trade) {
        NumberFormatter currenyFormatter = new NumberFormatter(NumberFormat.getCurrencyInstance(Locale.US));

        BigDecimal price = priceService.getPrice();
        BigDecimal amount = price.multiply(trade.getUnits(), MathContext.DECIMAL128);
        if(trade.getUnits().abs().doubleValue() < 0.0001) {
            throw new TradeException("Cannot trade in increments smaller than 0.0001");
        }

        if (trade.getUnits().compareTo(BigDecimal.ZERO) > 0) {
            //this is a purchase
            //lets see if we can get some money from the bank with the JAX-RS client

            //the transaction is represented by a JSONB object
            BankTransaction bankTransaction = new BankTransaction(trade.getName(), amount.negate());

            //we are using the new RX invoker to perform the invocation in an async manner
            ClientBuilder.newClient()
                    .target(TRANSACT + trade.getBankAccountNo())
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .rx() //tell the request to use the RX invoker
                    .post(Entity.entity(bankTransaction, MediaType.APPLICATION_JSON_TYPE))
                    .exceptionally((e -> Response.serverError().build())) //if there was an exception we treat is the same as a server error
                    .thenAccept(bankResponse -> {
                        //this callback gets called once the request is done
                        //check if the TX failed, if so we just respond with a server error
                        //ideally we would include this in the JSON response, but we are keeping things simple
                        if (bankResponse.getStatus() >= 300) {
                            response.resume(new TradeException("Unable to get funds from the bank to purchase Bitcoin, check your bank balance"));
                            return;
                        }
                        //bitcoin trades can take a while, we simulate this by scheduling a task to run later
                        managedScheduledExecutorService.schedule(new Runnable() {
                            @Override
                            public void run() {
                                //now we actually add the holdings
                                synchronized (holdings) {
                                    UserKey key = new UserKey(trade.getName(), trade.getBankAccountNo());
                                    BigDecimal currentHoldings = holdings.get(key);
                                    if (currentHoldings == null) {
                                        currentHoldings = BigDecimal.ZERO;
                                    }
                                    BigDecimal newHoldings = currentHoldings.add(trade.getUnits());
                                    holdings.put(key, newHoldings);
                                    try {
                                        //we publish the trade to the news stream
                                        //this uses the new CDI fireAsync method
                                        //so any problems with news stream consumers will not affect the trade
                                        newsEvent.fireAsync(trade.getName() + " just purchased " + trade.getUnits().setScale(3, RoundingMode.HALF_UP).toString() + " Bitcoin for " + currenyFormatter.valueToString(amount.abs()));
                                    } catch (ParseException e) {
                                        //will never happen
                                    }
                                    //send the JSONB response telling the client the trade was successful
                                    response.resume(new BitcoinTradeData(trade.getName(), trade.getBankAccountNo(), newHoldings));
                                }
                            }
                        }, new Random().nextInt(4) + 1, TimeUnit.SECONDS);

                    });

        } else {
            //for sales we reduce the holdings immediately
            synchronized (holdings) {
                UserKey key = new UserKey(trade.getName(), trade.getBankAccountNo());
                BigDecimal currentHoldings = holdings.get(key);
                if (currentHoldings == null) {
                    //they don't hold anything
                    response.resume(new TradeException("You don't hold any Bitcoin"));
                    return;
                }
                BigDecimal newHoldings = currentHoldings.add(trade.getUnits());
                if (newHoldings.compareTo(BigDecimal.ZERO) < 0) {
                    response.resume(new TradeException("You don't hold enough Bitcoin to complete the transaction"));
                    return;
                }
                holdings.put(key, newHoldings);
                //it takes a while for the money to actually come through
                //we process this async in the background though
                //we don't wait for this to happen before resuming though
                //so the client will have to wait for their money
                managedScheduledExecutorService.schedule(() -> {
                    //create a JSONB bank transaction
                    BankTransaction bankTransaction = new BankTransaction();
                    bankTransaction.setAmount(amount.negate());
                    bankTransaction.setName(trade.getName());


                    try (Response bankResponse = client
                            .target(TRANSACT + trade.getBankAccountNo())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.entity(bankTransaction, MediaType.APPLICATION_JSON_TYPE))) {
                        //we should probably check the response, but we are keeping this simple
                        //so if there is a problem with the bank the money just disappears
                    }

                }, new Random().nextInt(5) + 5, TimeUnit.SECONDS);
                try {
                    //publish the sale to the news stream
                    newsEvent.fireAsync(trade.getName() + " just sold " + trade.getUnits().setScale(3, RoundingMode.HALF_UP).toString() + " Bitcoin for " + currenyFormatter.valueToString(amount.abs()));
                } catch (ParseException e) {
                    //will never happen
                }
                //let the client know the results of the trade
                response.resume(new BitcoinTradeData(trade.getName(), trade.getBankAccountNo(), newHoldings));

            }
        }
    }

    @GET
    @Path("/holdings")
    @Produces(MediaType.APPLICATION_JSON)
    public List<BitcoinTradeData> allHoldings() {
        List<BitcoinTradeData> ret = new ArrayList<>();
        for (Map.Entry<UserKey, BigDecimal> e : holdings.entrySet()) {
            ret.add(new BitcoinTradeData(e.getKey().name, e.getKey().accountNo, e.getValue()));
        }
        return ret;
    }

    private static final class UserKey {
        private final String name;
        private final String accountNo;

        private UserKey(String name, String accountNo) {
            this.name = name;
            this.accountNo = accountNo;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UserKey userKey = (UserKey) o;
            return Objects.equals(name, userKey.name) &&
                    Objects.equals(accountNo, userKey.accountNo);
        }

        @Override
        public int hashCode() {

            return Objects.hash(name, accountNo);
        }
    }

}
