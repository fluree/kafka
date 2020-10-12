
VERSION := $(shell mvn org.apache.maven.plugins:maven-help-plugin:3.2.0:evaluate -Dexpression=project.version -q -DforceStdout 2>/dev/null)
VERSION ?= SNAPSHOT

MAJOR_VERSION := $(shell echo $(VERSION) | cut -d '.' -f1)
MINOR_VERSION := $(shell echo $(VERSION) | cut -d '.' -f2)

.PHONY: deps clean test jar uberjar docker docker-push docker-push-latest

target/fluree-kafka.standalone.jar: pom.xml src/**/* resources/**/*
	clojure -A:uberjar

build/fluree-kafka-$(VERSION).zip: stage-release
	cd build && zip -r fluree-$(VERSION).zip *

target/fluree-kafka.jar: pom.xml src/**/* resources/**/*
	clojure -A:jar

uberjar: target/fluree-kafka.standalone.jar

jar: target/fluree-kafka.jar

docker: target/fluree-kafka.standalone.jar
	docker build -t fluree/kafka:$(VERSION) .

docker-push: docker
	docker push fluree/kafka:$(VERSION)

docker-push-latest: docker-push
	docker tag fluree/kafka:$(VERSION) fluree/kafka:latest
	docker push fluree/kafka:latest

clean:
	rm -rf build
	rm -rf target

deps: deps.edn
	clojure -Stree

test:
	clojure -A:test
