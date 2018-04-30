package org.jboss.cryptotrader.bank;

import java.math.BigDecimal;
import java.security.SecureRandom;

import javax.ejb.Singleton;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonException;
import javax.json.JsonNumber;
import javax.json.JsonPointer;
import javax.json.JsonString;
import javax.json.JsonStructure;

/**
 * This class manages all account details.
 * <p>
 * Account details are stored in a JSON document, and modified with the JSON
 * patch API.
 * <p>
 * The document looks like:
 * <p>
 * {
 * "467597" :
 * {
 * client_name: "Joe Blogs",
 * balance: $3.50
 * }
 * "654536" :
 * {
 * client_name: "Jane Doe",
 * balance: $100
 * }
 * }
 * <p>
 * The only security that is applied is to make sure that the client name that is supplied matches
 * the account number.
 * <p>
 * This is somewhat contrived, but demonstrates simple usage of the patch API
 */
@Singleton
public class AccountManager {

    private JsonStructure accountDetails = Json.createObjectBuilder().build();

    public String openAccount(String clientName) {

        for (; ; ) {
            //we do it in a loop in case we double up account numbers

            //generate a random account number
            SecureRandom random = new SecureRandom(); //make it secure, we are a bank after all
            String accno = Integer.toString(1000000 + Math.abs(random.nextInt() % 9000000));

            //we check if the account number exists, we do this using the json pointer API
            //we expect this to throw an exception, if it does not then the account exists so try again
            String jsonPointerPath = "/" + accno;
            JsonPointer pointer = Json.createPointer(jsonPointerPath);
            try {
                pointer.getValue(accountDetails);
                //if this does not throw an exception the
                continue;
            } catch (JsonException expected) {

            }
            //now we create the new account
            JsonArray patch = Json.createArrayBuilder()
                    .add(
                            Json.createObjectBuilder()
                                    .add("op", "add")
                                    .add("path", jsonPointerPath)
                                    .add("value", Json.createObjectBuilder()
                                            .add("client_name", clientName)
                                            .add("balance", new BigDecimal(1000)) //all new clients get $1000
                                            .build()).build()).build();
            accountDetails = Json.createPatch(patch).apply(accountDetails);
            return accno;
        }
    }

    public BigDecimal transact(String accountNumber, String clientName, BigDecimal amount) {
        JsonNumber balance = (JsonNumber) accountDetails.getValue("/" + accountNumber + "/balance");
        BigDecimal newBalance = balance.bigDecimalValue().add(amount);

        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Insufficient funds");
        }

        JsonArray patch = Json.createArrayBuilder()
                .add(Json.createObjectBuilder()                     //first op is a test to validate the client name
                        .add("op", "test")
                        .add("path", "/" + accountNumber + "/client_name")
                        .add("value", clientName).build())
                .add(Json.createObjectBuilder()                     //first op is a test to validate the client name
                        .add("op", "replace")
                        .add("path", "/" + accountNumber + "/balance")
                        .add("value", newBalance).build())
                .build();
        accountDetails = Json.createPatch(patch).apply(accountDetails);
        return newBalance;
    }

    public BigDecimal getBalance(String accountNumber, String clientName) {
        JsonString cn = (JsonString) accountDetails.getValue("/" + accountNumber + "/client_name");
        if (!cn.getString().equals(clientName)) {
            throw new RuntimeException("Client name did not match");
        }
        JsonNumber balance = (JsonNumber) accountDetails.getValue("/" + accountNumber + "/balance");
        return balance.bigDecimalValue();
    }

}
