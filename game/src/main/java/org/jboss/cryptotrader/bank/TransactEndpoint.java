package org.jboss.cryptotrader.bank;

import java.math.BigDecimal;

import javax.enterprise.context.Dependent;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.json.JsonObject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;

@Path("/bank/transact")
@Dependent
public class TransactEndpoint {

    @Inject
    private AccountManager accountManager;

    @Inject
    private Event<TransactionEvent> event;

    @Path("/{accountNo}")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public void transact(JsonObject jsonObject, @PathParam("accountNo") String accountNo) {
        String clientName = jsonObject.getString("name");
        BigDecimal amount = jsonObject.getJsonNumber("amount").bigDecimalValue();
        BigDecimal newBalance = accountManager.transact(accountNo, clientName, amount);
        event.fire(new TransactionEvent(accountNo, clientName, newBalance));
    }

}
