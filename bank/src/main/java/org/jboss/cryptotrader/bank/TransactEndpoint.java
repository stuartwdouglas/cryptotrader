package org.jboss.cryptotrader.bank;

import java.math.BigDecimal;

import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.json.JsonObject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

@Path("/transact")
public class TransactEndpoint {

    @Inject
    private AccountManager accountManager;

    @Inject
    private Event<TransactionEvent> event;

    @Path("/")
    @POST
    public void transact(JsonObject jsonObject) {
        String account = jsonObject.getString("accountNo");
        String clientName = jsonObject.getString("name");
        BigDecimal amount = jsonObject.getJsonNumber("amount").bigDecimalValue();
        BigDecimal newBalance = accountManager.transact(account, clientName, amount);
        event.fire(new TransactionEvent(account, clientName, newBalance));
    }

}
