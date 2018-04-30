

oc new-project cryptotrader

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
