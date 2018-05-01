package org.jboss.cryptotrader.bitcoin;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.swing.text.NumberFormatter;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/bitcoin/trade")
@ApplicationScoped
public class BitcoinExchangeEndpoint {

    private static final String TRANSACT;

    static {
        String host;
        try {
            InetAddress.getByName("game");
            host = "http://game:8080/game/rest/bank/transact/";
        } catch (UnknownHostException e) {
            host = "http://localhost:8080/game/rest/bank/transact/";
        }
        TRANSACT = host;

    }
    /**
     * We just track holdings in a map keyed by client name
     */
    private final Map<UserKey, BigDecimal> holdings = new HashMap<>();

    @Inject
    private BitcoinPriceService priceService;

    @Resource
    private ManagedScheduledExecutorService managedScheduledExecutorService;

    @Inject
    @BitcoinNews
    private Event<String> newsEvent;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON})
    public BitcoinTradeData trade(BitcoinTradeData trade) throws ParseException {
        NumberFormatter currenyFormatter = new NumberFormatter(NumberFormat.getCurrencyInstance(Locale.US));
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
                UserKey key = new UserKey(trade.getName(), trade.getBankAccountNo());
                BigDecimal currentHoldings = holdings.get(key);
                if (currentHoldings == null) {
                    currentHoldings = BigDecimal.ZERO;
                }
                BigDecimal newHoldings = currentHoldings.add(trade.getUnits());
                holdings.put(key, newHoldings);
                newsEvent.fireAsync(trade.getName() + " just purchased " + trade.getUnits().setScale(3, RoundingMode.HALF_UP).toString() + " Bitcoin for " + currenyFormatter.valueToString(amount.abs()));
                return new BitcoinTradeData(trade.getName(), trade.getBankAccountNo(), newHoldings);
            }
        } else {

            synchronized (holdings) {
                UserKey key = new UserKey(trade.getName(), trade.getBankAccountNo());
                BigDecimal currentHoldings = holdings.get(key);
                if (currentHoldings == null) {
                    throw new RuntimeException("You don't hold any Bitcoin");
                }
                BigDecimal newHoldings = currentHoldings.add(trade.getUnits());
                if (newHoldings.compareTo(BigDecimal.ZERO) < 0) {
                    throw new RuntimeException("You don't hold enough Bitcoin to complete the transaction");
                }
                holdings.put(key, newHoldings);
                //it takes a while for the money to actually come through
                //we process this async in the background though
                managedScheduledExecutorService.schedule(() -> {
                    BankTransaction bankTransaction = new BankTransaction();
                    bankTransaction.setAmount(amount.negate());
                    bankTransaction.setName(trade.getName());

                    try (Response bankResponse = ClientBuilder.newClient()
                            .target(TRANSACT + trade.getBankAccountNo())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.entity(bankTransaction, MediaType.APPLICATION_JSON_TYPE))) {
                        //we should probably check the response, but we are keeping this simple
                        //so if there is a problem with the bank the money just disappears
                    }

                }, new Random().nextInt(5) + 5, TimeUnit.SECONDS);
                newsEvent.fireAsync(trade.getName() + " just sold " + trade.getUnits().setScale(3, RoundingMode.HALF_UP).toString() + " Bitcoin for " + currenyFormatter.valueToString(amount.abs()));
                return new BitcoinTradeData(trade.getName(), trade.getBankAccountNo(), newHoldings);

            }
        }
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
