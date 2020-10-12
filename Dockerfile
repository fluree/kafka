FROM openjdk:11

COPY target/fluree-kafka.standalone.jar /home/fluree-kafka.jar
COPY resources/logback.xml /home/logback.xml

ENV FLUREE_SERVERS=http://localhost:8080 \
    FLUREE_LEDGER=my/ledger \
    KAFKA_SERVERS=localhost:9092 \
    KAFKA_TOPIC=fluree

CMD ["java", "-Xms2g", "-Xmx2g", "-jar", "/home/fluree-kafka.jar"]
