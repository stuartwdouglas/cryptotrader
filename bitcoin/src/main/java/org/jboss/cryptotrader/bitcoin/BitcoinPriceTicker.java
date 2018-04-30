package org.jboss.cryptotrader.bitcoin;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.inject.Inject;

@Singleton
@Startup
public class BitcoinPriceTicker {

    /**
     * We will update the price every second, using an EE concurrent
     * scheduled executor.
     */
    @Resource
    private ManagedScheduledExecutorService priceTicker;

    @Inject
    private BitcoinPriceService service;

    private ScheduledFuture<?> handle;

    @PostConstruct
    private void setup() {
        handle = priceTicker.scheduleAtFixedRate(() -> {
           service.updatePrice();
        }, 1, 1, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop() {
        handle.cancel(true);
    }
}
