package org.jboss.cryptotrader.bitcoin;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.enterprise.context.Dependent;
import javax.enterprise.event.ObservesAsync;
import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Endpoint that can be used to query the bitcoin price.
 *
 * This observes price changes and then writes the price as a plain string to the response
 */
@WebServlet(urlPatterns = {"/watch-text"}, asyncSupported = true)
@Dependent
public class BitcoinPriceWatcherServlet extends HttpServlet {

    private static final Set<HttpServletResponse> textResponses = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static void priceChange(@ObservesAsync @BitcoinPriceChange BigDecimal price) {
        byte[] response = (price.toString() + "\n").getBytes(StandardCharsets.US_ASCII);
        Iterator<HttpServletResponse> it = textResponses.iterator();
        while (it.hasNext()) {
            HttpServletResponse next = it.next();
            try {
                next.getOutputStream().write(response);
                next.getOutputStream().flush();
            } catch (IOException e) {
                it.remove();
            }
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        AsyncContext context = req.startAsync();
        context.setTimeout(Long.MAX_VALUE);
        context.addListener(new AsyncListener() {
            @Override
            public void onComplete(AsyncEvent event) {
                textResponses.remove(resp);
            }

            @Override
            public void onTimeout(AsyncEvent event) throws IOException {
                textResponses.remove(resp);
                resp.getOutputStream().close();
            }

            @Override
            public void onError(AsyncEvent event) throws IOException {
                textResponses.remove(resp);
                resp.getOutputStream().close();
            }

            @Override
            public void onStartAsync(AsyncEvent event) {

            }
        });
        textResponses.add(resp);
    }

}
