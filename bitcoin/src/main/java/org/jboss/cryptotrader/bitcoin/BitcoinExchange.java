package org.jboss.cryptotrader.bitcoin;

import java.math.BigDecimal;
import java.math.MathContext;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/exchange")
@ApplicationScoped
public class BitcoinExchange {

    private static final String TRANSACT;

    static {
        String host;
        try {
            InetAddress.getByName("bitcoin.eap-demo.svc");
            host = "http://bitcoin.eap-demo.svc:8080/game/bank/transact/";
        } catch (UnknownHostException e) {
            host = "http://localhost:8080/game/bank/transact/";
        }
        TRANSACT = host;

    }
    /**
     * We just track holdings in a map keyed by client name
     */
    private final Map<String, BigDecimal> holdings = new HashMap<>();

    @Inject
    private BitcoinPriceService priceService;

    @Resource
    private ManagedScheduledExecutorService managedScheduledExecutorService;

    @GET
    @Produces({MediaType.TEXT_PLAIN})
    @Path("/holdings/{name}")
    public BigDecimal holdings(@PathParam("name") String name) {
        return holdings.get(name);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.TEXT_PLAIN})
    public BigDecimal trade(BitcoinTrade trade) {
        BigDecimal price = priceService.getPrice();
        BigDecimal amount = price.multiply(trade.getUnits(), MathContext.DECIMAL128);
        if (trade.getUnits().compareTo(BigDecimal.ZERO) > 0) {
            //this is a purchase
            //lets see if we can get some money from the bank with the JAX-RS client

            BankTransaction bankTransaction = new BankTransaction();
            bankTransaction.setAmount(amount.negate());
            bankTransaction.setName(trade.getName());

            try (Response bankResponse = ClientBuilder.newClient()
                    .target(TRANSACT + trade.getBankAccountNo())
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .post(Entity.entity(bankTransaction, MediaType.APPLICATION_JSON_TYPE))) {
                if (bankResponse.getStatus() >= 300) {
                    throw new RuntimeException("Unable to get funds from the bank to purchase bitcoin");
                }
            }

            //it takes a while for the trade to go through
            //so we sleep for a while
            try {
                Thread.sleep(1000 * new Random().nextInt(5));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            synchronized (holdings) {
                BigDecimal currentHoldings = holdings.get(trade.getName());
                if (currentHoldings == null) {
                    currentHoldings = BigDecimal.ZERO;
                }
                BigDecimal newHoldings = currentHoldings.add(trade.getUnits());
                holdings.put(trade.getName(), newHoldings);
                return newHoldings;
            }
        } else {

            synchronized (holdings) {
                BigDecimal currentHoldings = holdings.get(trade.getName());
                if (currentHoldings == null) {
                    throw new RuntimeException("You don't hold any bitcoin");
                }
                BigDecimal newHoldings = currentHoldings.add(trade.getUnits());
                if (newHoldings.compareTo(BigDecimal.ZERO) < 0) {
                    throw new RuntimeException("You don't hold enough bitcoin to complete the transaction");
                }
                holdings.put(trade.getName(), newHoldings);
                //it takes a while for the money to actually come through
                //we process this async in the background though
                managedScheduledExecutorService.schedule(() -> {
                    BankTransaction bankTransaction = new BankTransaction();
                    bankTransaction.setAmount(amount);
                    bankTransaction.setName(trade.getName());

                    try (Response bankResponse = ClientBuilder.newClient()
                            .target(TRANSACT + trade.getBankAccountNo())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.entity(bankTransaction, MediaType.APPLICATION_JSON_TYPE))) {
                        //we should probably check the response, but we are keeping this simple
                        //so if there is a problem with the bank the money just disappears
                    }

                }, new Random().nextInt(5) + 5, TimeUnit.SECONDS);
                return newHoldings;

            }
        }
    }

}
