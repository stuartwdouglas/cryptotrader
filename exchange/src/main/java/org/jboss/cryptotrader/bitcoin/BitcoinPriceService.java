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
import java.util.Random;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;

/**
 * An application scoped bean that manages the bitcoin price
 *
 * In general the price will trend in a certain direction for a random period
 * of time, then pick a new trend direction. Moderate trends are more likely
 * than extreme events.
 *
 * At some time after a trend has started a news event may be created to give
 * the player an idea of the direction of the current trend.
 *
 */
@ApplicationScoped
public class BitcoinPriceService {

    /**
     * We don't allow the price to get this high, if we hit 20k there will be a big crash
     */
    private static final BigDecimal CRASH_CEILING = new BigDecimal("20000");

    /**
     * Our price generator
     */
    private final Random random = new Random();

    /**
     * This is the general trend of the market. If it is positive the market
     * will trend up, negative it will trend down.
     */
    private BigDecimal marketDirection = BigDecimal.ZERO;

    /**
     * The number of 'ticks' that the current market conditions will continue
     */
    private int ticksTillConditionsChange = 5;

    /**
     * The current price
     */
    private volatile BigDecimal price = BigDecimal.ONE;

    /**
     * The potential news message for the current trend
     */
    private String newsMessage;
    /**
     * The number of ticks before the news message is sent
     */
    private int messageTicks;

    @Inject
    @BitcoinNews
    private Event<String> newsEvents;

    @Inject
    @BitcoinPriceChange
    private Event<BigDecimal> priceChangeEvent;

    public synchronized void updatePrice() {
        if (--ticksTillConditionsChange == 0) {
            //we generate a number from 0-99, and use that to
            int direction = random.nextInt(100);
            if (direction <= 3) {
                //CRASH
                marketDirection = BigDecimal.valueOf((random.nextDouble() * -0.1) - 0.02);
                ticksTillConditionsChange = random.nextInt(10) + 5; //crashs are short and sharp
                newsMessage = "Bitcoint is experiencing a correction";
                messageTicks = random.nextInt(5);
            } else if(direction <= 10) {
                //RUSH
                marketDirection = BigDecimal.valueOf((random.nextDouble() * 0.1) + 0.01);
                ticksTillConditionsChange = random.nextInt(20) + 5;
                newsMessage = "The price of bitcoin is skyrocketing, everyone is buying in";
                messageTicks = random.nextInt(15);
            } else if(direction <= 40) {
                //BEAR MARKET
                marketDirection = BigDecimal.valueOf((random.nextDouble() * -0.03));
                ticksTillConditionsChange = random.nextInt(20) + 15;
                newsMessage = "Bitcoin seems to be in a bear market at the moment";
                messageTicks = random.nextInt(15) + 5;
            } else {
                //BULL MARKET
                marketDirection = BigDecimal.valueOf(random.nextDouble() * 0.05);
                ticksTillConditionsChange = random.nextInt(20) + 15;
                newsMessage = "Bitcoin seems to be in a bull market at the moment";
                messageTicks = random.nextInt(15) + 5;
            }
        } else if(price.compareTo(CRASH_CEILING) > 0) {
            //if the price gets too high there will be a big crash
            marketDirection = BigDecimal.valueOf((random.nextDouble() * -0.1) - 0.1);
            ticksTillConditionsChange = random.nextInt(10) + 10;
            newsMessage = "Bitcoin is crashing hard";
            messageTicks = random.nextInt(5) + 2;
        }
        if(newsMessage != null) {
            if(--messageTicks == 0) {
                //send out a message that gives the player a hint as to the current direction
                newsEvents.fireAsync(newsMessage);
                newsMessage = null;
            }
        }

        double change = random.nextDouble() * 0.02 - 0.01; //the random part of the price change
        price = price.add(price.multiply(marketDirection.add(BigDecimal.valueOf(change)))); //calculate a new price
        priceChangeEvent.fireAsync(price); //notify the world of the new price
    }

    public BigDecimal getPrice() {
        return price;
    }
}
