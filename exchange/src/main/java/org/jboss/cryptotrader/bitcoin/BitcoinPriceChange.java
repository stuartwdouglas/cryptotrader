package org.jboss.cryptotrader.bitcoin;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Qualifier;

/**
 * Qualifier that is used to broadcast the price change event
 */
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface BitcoinPriceChange {

    class Literal extends AnnotationLiteral<BitcoinPriceChange> implements BitcoinPriceChange {

    }

}
