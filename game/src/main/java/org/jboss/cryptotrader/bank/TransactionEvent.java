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

package org.jboss.cryptotrader.bank;

import java.math.BigDecimal;

/**
 * CDI event that is fired when a bank transaction occurs
 */
public class TransactionEvent {

    private final String account;
    private final String client;
    private final BigDecimal balance;

    public TransactionEvent(String account, String client, BigDecimal balance) {
        this.account = account;
        this.client = client;
        this.balance = balance;
    }

    public String getAccount() {
        return account;
    }

    public String getClient() {
        return client;
    }

    public BigDecimal getBalance() {
        return balance;
    }
}
