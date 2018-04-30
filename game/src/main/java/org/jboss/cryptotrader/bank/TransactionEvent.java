package org.jboss.cryptotrader.bank;

import java.math.BigDecimal;

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
