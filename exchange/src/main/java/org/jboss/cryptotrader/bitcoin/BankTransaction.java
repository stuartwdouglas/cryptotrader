package org.jboss.cryptotrader.bitcoin;

import java.math.BigDecimal;

import javax.json.bind.annotation.JsonbAnnotation;

@JsonbAnnotation
public class BankTransaction {

    private String name;
    private BigDecimal amount;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
