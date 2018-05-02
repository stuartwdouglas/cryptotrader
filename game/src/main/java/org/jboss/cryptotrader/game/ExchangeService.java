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

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Class that figures out the URI of the exchange service. For real apps this should not be hard coded
 */
class ExchangeService {

    private static final String SERVICE_NAME = System.getProperty("cryptotrader.exchange", "exchange");

    private static final String BASE_URL;
    public static final String BITCOIN_PRICE_WATCH;
    public static final String BITCOIN_PRICE;
    public static final String BITCOIN_TRADE;
    public static final String BITCOIN_NEWS;
    public static final String BITCOIN_ALL_HOLDINGS;

    static {
        String host;
        try {
            InetAddress address = InetAddress.getByName(SERVICE_NAME);
            System.out.println("EXCHANGE_SERVICE: Using openshift services, resolved IP: " + address);
            host = "http://" + SERVICE_NAME + ":8080/exchange";
        } catch (UnknownHostException e) {
            host = "http://localhost:8080/exchange";
        }
        BASE_URL = host;
        BITCOIN_PRICE_WATCH = BASE_URL + "/bitcoin/price/watch";
        BITCOIN_PRICE = BASE_URL + "/bitcoin/price";
        BITCOIN_TRADE = BASE_URL + "/bitcoin/trade";
        BITCOIN_ALL_HOLDINGS = BASE_URL + "/bitcoin/trade/holdings";
        BITCOIN_NEWS = BASE_URL + "/bitcoin/news";
    }
}
