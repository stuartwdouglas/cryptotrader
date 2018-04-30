package org.jboss.cryptotrader.bank;

import java.math.BigDecimal;

import org.junit.Assert;
import org.junit.Test;

public class AccountManagerTestCase {

    private static final String STUART = "Stuart";
    private static final String JOE = "Joe";

    @Test
    public void testAccountManager() {
        AccountManager manager = new AccountManager();
        String stuartNo = manager.openAccount(STUART);

        Assert.assertEquals(1000, manager.getBalance(stuartNo, STUART).intValue());

        //now test the security
        try {
            manager.getBalance(stuartNo, JOE);
            Assert.fail();
        } catch (RuntimeException expected) {

        }

        manager.transact(stuartNo, STUART, new BigDecimal(100));

        Assert.assertEquals(1100, manager.getBalance(stuartNo, STUART).intValue());
        try {
            manager.transact(stuartNo, JOE, BigDecimal.valueOf(-100));
            Assert.fail();
        } catch (RuntimeException expected) {

        }

        //not enough money
        try {
            manager.transact(stuartNo, STUART, BigDecimal.valueOf(-2000));
            Assert.fail();
        } catch (RuntimeException expected) {

        }

    }

}
