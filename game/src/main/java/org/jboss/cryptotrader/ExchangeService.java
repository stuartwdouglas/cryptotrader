package org.jboss.cryptotrader;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class ExchangeService {

    private static final String BASE_URL;
    public static final String BITCOIN_WATCH;
    public static final String BITCOIN_TRADE;

    static {
        String host;
        try {
            InetAddress.getByName("exchange");
            host = "http://exchange:8080/exchange";
        } catch (UnknownHostException e) {
            host = "http://localhost:8080/exchange";
        }
        BASE_URL = host;
        BITCOIN_WATCH = BASE_URL + "/bitcoin/price/watch";
        BITCOIN_TRADE = BASE_URL + "/bitcoin/trade";
    }
}
