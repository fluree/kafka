# Sample Fluree Kafka Connector

This sample illustrates using the Fluree `listen` API to listen to all changes
in a Fluree ledger and then pushes new block data to a Kafka topic.

The `listen` api has the signature of `(listen conn ledger listener-key listen-fn)`
where the `listener-fn` is a two-argument function that will be called with every
new event in the specified `ledger`. The first argument is the event type,
in this case we are only looking for the `:block` event which broadcasts new blocks.
The second argument is the event data. 

This example also massages the block data prior to sending it onto the Kafka topic
using the `block-event->map` api, which takes the raw Flakes (extended RDF data)
from a block event and adds both an :added and :retracted key with maps (JSON objects)
containing each of the added and retracted subjects with their respective predicates
and values. 

The `listener-key` is any unique key for your listener and is only used to
close a listener via `(close-listener conn ledger listener-key)`. In this example
it is used to close the listener at shutdown in a controlled order,
however the resources would be released for this listener at shutdown regardless
of if it was explicitlly closed.

## Running as a Docker container

`docker run --env FLUREE_SERVERS=http://localhost:8080 --env FLUREE_LEDGER=my/ledger --env KAFKA_SERVERS=localhost:9092 --env KAFKA_TOPIC=test fluree/kafka:latest`

## Running as a .jar file
If the necessary environment variables are already set, run:

`java -jar target/fluree-kafka.standalone.jar`

Or, set the environment variables in the same command like:

`FLUREE_SERVERS=http://localhost:8080 FLUREE_LEDGER=my/ledger KAFKA_SERVERS=localhost:9092 KAFKA_TOPIC=test java -jar target/fluree-kafka.standalone.jar`

## Logging
Kafka and Fluree both use the slf4j / logback format. You can add/modify
the logback.xml to control logging levels, output, etc.

## Building
The build process uses [make](https://en.wikipedia.org/wiki/Make_(software)) and the [clojure cli](https://clojure.org/guides/getting_started) along with Clojure's `deps.edn` 
dependency management process. The build process generates a [Java](https://www.java.com/en/download/) .jar file. Be sure these tools are available in your local environment.
If building a Docker image, [docker](https://docs.docker.com/get-docker/) must also be installed locally.

### Building an Uberjar
To build an uberjar simply type `make` or `make uberjar` from the command
line within this repository's root directory.

### Building a Docker Container
Run `make docker`. An uberjar will be built and then packed up in the `Dockerfile` included in
this repository.

To push the Docker container to the central container registry, run `make docker-push`. The container
will be tagged by the repository version (defined in the `pom.xml` file).

To push the Docker container, and also tag it as `:latest`, run `make docker-push-latest`.

### Updating Version
To update the version of this project/repository, update `<version>...</version>` in the `pom.xml` file.

## Limitations

### Checkpointing 
This example does not checkpoint the block it has processed - and therefore
if this service failed and then resumed, it could miss blocks. If important
for the application, it could be solved in a number of ways. 

a) By the Kafka Consumer: The consumer could read events and if it
discovered a gap in block number (the block number is included in the event data),
it could use the `(block-range db from-block to-block)` api to get and
process any missing blocks before proceeding.

b) By this Kafka Producer: This connector could alternatively be updated to include a
Kafka consumer that upon startup, seek'd to the last offset and read the last event, 
subsequently sending any block data gaps using the above method. This could
work most effectively if the Kafka topic was only being used for Fluree events,
otherwise it would have to read backwards from the last offset until it found
the last block event data - something Kafka isn't designed to naturally do.

c) Using Fluree: This connector could transact the last processed block on
some period that made sense for the application into a Fluree predicate used
specifically for this connector. On startup, this connector could read the
last checkpointed block and 'catch up' as necessary. To avoid an infinite loop, either a different
ledger should be used for this, or this producer would want to filter out
those transactions and not checkpoint or propagate them.

d) If running Kafka, it also means you are running Zookeeper. Zookeeper does this
sort of thing for a living.

### Duplicates
If it is possible multiple of this service could be running at the same time,
i.e. perhaps via a rolling upgrade, it would mean the same block could be 
pushed onto the Kafka topic multiple times. This could be handled by the
consumer tracking the last processed block in local state and skipping any
duplicates. It would be important the consumer checkpoints its offset in
the Kafka topics to avoid its own failure scenario where a Kafka offset/index
is transacted multiple times.

An alternative could be to create a 'lock' by extending (c) above to also
include a server-name, and ideally a lock-time, that would allow any other
service to see that another server is actively processing events and it should
stand-by.

Another option if leaning into Zookeeper as per (d) above is to just utilize it.




