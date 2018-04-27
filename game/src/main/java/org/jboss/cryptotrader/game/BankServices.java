package org.jboss.cryptotrader.game;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

public class BankServices {

    static final String BASE_URI;

    static {
        String host = "localhost";
        try {
            InetAddress address = InetAddress.getByName("bank");
            //we are running in minishift, use 'bank' as the hostname
            host = "bank";
        } catch (UnknownHostException e) {
        }
        BASE_URI = "http://" + host + ":8080/bank";
    }

    public static URI open() {
        try {
            return new URI(BASE_URI + "/open");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static URI balance(String accountNo) {
        try {
            return new URI(BASE_URI + "/balance/" + accountNo);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static URI watch(String accountNo) {
        try {
            return new URI(BASE_URI + "/balance/watch/" + accountNo);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static URI transact(String accountNo) {
        try {
            return new URI(BASE_URI + "/transact/" + accountNo);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
