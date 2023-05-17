FROM ubuntu:20.04

ENV TZ=Europe/London
RUN ln -snf "/usr/share/zoneinfo/$TZ" /etc/localtime
RUN echo "$TZ" > /etc/timezone

RUN DEBIAN_FRONTEND=noninteractive \
    && apt-get update \
    && apt-get upgrade -y \
    && apt-get install -y ca-certificates-java \
    && apt-get install -y openjdk-17-jre-headless \
    && rm -rf /var/lib/apt/lists/*

COPY target/scala-2.13/attachment.jar /bin

COPY test.txm.pem /tmp/test.txm.pem
RUN keytool -noprompt -import -trustcacerts -alias test.txm -file /tmp/test.txm.pem -keystore /usr/lib/jvm/java-17-openjdk-amd64/lib/security/cacerts -storepass changeit
COPY prod.txm.pem /tmp/prod.txm.pem
RUN keytool -noprompt -import -trustcacerts -alias prod.txm -file /tmp/prod.txm.pem -keystore /usr/lib/jvm/java-17-openjdk-amd64/lib/security/cacerts -storepass changeit

COPY txm.external.crt.pem /tmp/txm.external.crt.pem
RUN keytool -noprompt -import -trustcacerts -alias txm.external -file /tmp/txm.external.crt.pem -keystore /usr/lib/jvm/java-17-openjdk-amd64/lib/security/cacerts -storepass changeit
COPY prod.txm.external.crt.pem /tmp/prod.txm.external.crt.pem
RUN keytool -noprompt -import -trustcacerts -alias prod.txm.external -file /tmp/prod.txm.external.crt.pem -keystore /usr/lib/jvm/java-17-openjdk-amd64/lib/security/cacerts -storepass changeit

CMD java $JAVA_OPTS -jar /bin/attachment.jar