package org.jboss.cryptotrader.game;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

import javax.servlet.ServletContext;

import io.undertow.predicate.Predicates;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PredicateHandler;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.servlet.ServletExtension;
import io.undertow.servlet.api.DeploymentInfo;

public class ProxyExtension implements ServletExtension {
    @Override
    public void handleDeployment(DeploymentInfo deploymentInfo, ServletContext servletContext) {
        tryProxyService(deploymentInfo, "bitcoin");
        tryProxyService(deploymentInfo, "bank");
    }

    private void tryProxyService(DeploymentInfo deploymentInfo, String service) {
        System.out.println("Attempting to install proxy for " + service);
        try {
            InetAddress target = InetAddress.getByName(service + ".eap-demo.svc");
            deploymentInfo.addOuterHandlerChainWrapper(new HandlerWrapper() {
                @Override
                public HttpHandler wrap(HttpHandler httpHandler) {
                    try {
                        ProxyHandler proxyHandler = new ProxyHandler(new LoadBalancingProxyClient()
                                .addHost(new URI("http://" + service + ".eap-demo.svc:8080")), httpHandler);
                        return new PredicateHandler(Predicates.prefix("/" + service), proxyHandler, httpHandler);
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                }
            });

        } catch (UnknownHostException e) {
            //this will happen when running outside openshift
            System.out.println("Unable to resolve host " + service + " proxy setup will not be created");
        }
    }
}
