FROM alpine:latest

RUN apk --no-cache add openjdk11-jdk

COPY target/scala-2.13/attachment.jar /bin

COPY test.txm.pem /tmp/test.txm.pem
RUN keytool -noprompt -import -trustcacerts -alias test.txm -file /tmp/test.txm.pem -keystore /usr/lib/jvm/java-11-openjdk/lib/security/cacerts -storepass changeit
COPY prod.txm.pem /tmp/prod.txm.pem
RUN keytool -noprompt -import -trustcacerts -alias prod.txm -file /tmp/prod.txm.pem -keystore /usr/lib/jvm/java-11-openjdk/lib/security/cacerts -storepass changeit

CMD java $JAVA_OPTS -jar /bin/attachment.jar