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

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.inject.Inject;

/**
 * A singleton startup bean that drives the price changes
 */
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
