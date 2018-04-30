package org.jboss.cryptotrader.bank;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/bank/open")
@Dependent
public class OpenEndpoint {

    @Inject
    private AccountManager accountManager;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public JsonObject transact(JsonObject jsonObject) {
        String clientName = jsonObject.getString("name");
        String accountNo = accountManager.openAccount(clientName);
        return Json.createObjectBuilder().add("accountNo", accountNo)
                .add("name", clientName)
                .add("balance", accountManager.getBalance(accountNo, clientName))
                .build();
    }

}
