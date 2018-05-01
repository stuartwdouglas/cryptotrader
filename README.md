# Crytotrader Demo

This is a simple demo that demonstrates some of the new features of Java EE that are available in EAP CD 12.

This application is a very simple game, that lets you trade Bitcoin. It takes the form of two different microservices:

* Game

   This provides the front end, and also manages the players bank balance. 
   
* Exchange

   This simulates a Bitcoin exchange, and peforms trades and maintains the players Bicoin balance.
   
The new features in EE8 that are highlighted are:

* Server Sent Events

   The demo uses server sent events to send data such as pricing between both the exchange and game services,
   and between the game and the browser. JAX-RS 2.1 includes support for both sending a receiving server sent
   events.
   
* JAX-RS Reactive HTTP Client

   The demo uses the new JAX-RS reactive HTTP client to make async service invocations.
   
* JSONB support

    JSONB allows Java objects to be directly mapped to Java objects. This is used in the exchange module to map
    request and response objects to and from JSON.
    
* JSONP Pointer and Patch Support

    The JSON pointer API allows you to reference parts of a JSON document, and the patch API allows you to modify it.
    This is used by the bank service to search and update a JSON document that contains all bank account details.
    
* Default context root

    The default context root of the application can now be specified in web.xml, so you can deploy war files with a
    different context root to the file name.

## Architecture and Messages

The demo application is very asynchronous, with most data being passed around asynchronously via server sent events
or using the JAX-RS reactive client.

The basic architecture diagram is shown below:

 


## Front End

The front end is a single page app that communicates with the `game` module using the standard `fetch` API and
`EventSource` to subscribe to server send events.

The front end is written in React, using react development mode to avoid the need for any native executables as part of
the build to make is easy to demonstrate. This approach should not be used for production applications, which should be 
pre-compiled beforehand as per the React documentation.


## Deploying to Openshift/Minishift

To deploy to Openshift or Minishift make sure you are logged in, then run the following commands.
This will deploy on the EAP CD 12 image.

Note that if you are using openshift starter this will use up most of your resource allocation,
so if you have other applications running then these commands will likely fail.

```oc new-project cryptotrader

for resource in \
  eap-cd-image-stream.json \
  eap-cd-amq-persistent-s2i.json \
  eap-cd-amq-s2i.json \
  eap-cd-basic-s2i.json \
  eap-cd-https-s2i.json \
  eap-cd-mongodb-persistent-s2i.json \
  eap-cd-mongodb-s2i.json \
  eap-cd-mysql-persistent-s2i.json \
  eap-cd-mysql-s2i.json \
  eap-cd-postgresql-persistent-s2i.json \
  eap-cd-postgresql-s2i.json \
  eap-cd-sso-s2i.json
do
  oc replace --force -f \
https://raw.githubusercontent.com/jboss-container-images/jboss-eap-7-openshift-image/eap-cd/templates/${resource}
done

oc new-app --template=eap-cd-basic-s2i -p IMAGE_STREAM_NAMESPACE="cryptotrader" -p SOURCE_REPOSITORY_URL="https://github.com/stuartwdouglas/cryptotrader.git" -p CONTEXT_DIR=game -p MEMORY_LIMIT="490Mi" -p APPLICATION_NAME="game" -p SOURCE_REPOSITORY_REF=master JAVA_OPTS_APPEND=-Dee8.preview.mode=true
oc new-app --template=eap-cd-basic-s2i -p IMAGE_STREAM_NAMESPACE="cryptotrader" -p SOURCE_REPOSITORY_URL="https://github.com/stuartwdouglas/cryptotrader.git" -p CONTEXT_DIR=exchange -p MEMORY_LIMIT="490Mi" -p APPLICATION_NAME="exchange" -p SOURCE_REPOSITORY_REF=master JAVA_OPTS_APPEND=-Dee8.preview.mode=true
```