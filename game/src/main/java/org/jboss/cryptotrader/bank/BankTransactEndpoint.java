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

import javax.enterprise.context.Dependent;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;

/**
 * Endpoint that performs bank transactions, it delegates all the actual work to
 * {@link AccountManager}
 */
@Path("/bank/transact")
@Dependent
public class BankTransactEndpoint {

    @Inject
    private AccountManager accountManager;

    @Inject
    private Event<TransactionEvent> event;

    @Path("/{accountNo}")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public void transact(JsonObject jsonObject, @PathParam("accountNo") String accountNo) {
        String clientName = jsonObject.getString("name");
        JsonValue amt = jsonObject.get("amount");
        BigDecimal amount;
        if(amt.getValueType() == JsonValue.ValueType.NUMBER) {
            amount = ((JsonNumber)amt).bigDecimalValue();
        } else {
            amount = new BigDecimal(((JsonString)amt).getString());
        }
        BigDecimal newBalance = accountManager.transact(accountNo, clientName, amount);
        event.fire(new TransactionEvent(accountNo, clientName, newBalance));
    }

}
