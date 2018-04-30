package org.jboss.cryptotrader.bitcoin;

import java.math.BigDecimal;
import java.util.Random;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;

/**
 * An application scoped bean that manages the bitcoin price
 */
@ApplicationScoped
public class BitcoinPriceService {

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
     * Our price generator
     */
    private final Random random = new Random();


    private volatile BigDecimal price = BigDecimal.ONE; //we start out at one

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
                System.out.println("CRASH " + marketDirection + " " + ticksTillConditionsChange);
            } else if(direction <= 10) {
                //RUSH
                marketDirection = BigDecimal.valueOf((random.nextDouble() * 0.1) + 0.01);
                ticksTillConditionsChange = random.nextInt(20) + 5;
                System.out.println("RUSH " + marketDirection + " " + ticksTillConditionsChange);
            } else if(direction <= 40) {
                //BEAR MARKET
                marketDirection = BigDecimal.valueOf((random.nextDouble() * -0.03));
                ticksTillConditionsChange = random.nextInt(20) + 15;
                System.out.println("BEAR MARKET " + marketDirection + " " + ticksTillConditionsChange);
            } else {
                //BULL MARKET
                marketDirection = BigDecimal.valueOf(random.nextDouble() * 0.05);
                ticksTillConditionsChange = random.nextInt(20) + 15;
                System.out.println("BULL MARKET " + marketDirection + " " + ticksTillConditionsChange);
            }
        }

        double change = random.nextDouble() * 0.02 - 0.01;
        price = price.add(price.multiply(marketDirection).add(BigDecimal.valueOf(change)));
        priceChangeEvent.fire(price);
    }

    public BigDecimal getPrice() {
        return price;
    }
}
