package org.jboss.cryptotrader.game;

import java.math.BigDecimal;

import javax.json.bind.annotation.JsonbAnnotation;

@JsonbAnnotation
public class BankAccount {

    private String accountNo;
    private String name;
    private BigDecimal balance;

    public String getAccountNo() {
        return accountNo;
    }

    public void setAccountNo(String accountNo) {
        this.accountNo = accountNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
}
