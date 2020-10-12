(ns fluree.kafka.core
  (:gen-class)
  (:require [fluree.db.api :as fdb]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [fluree.db.util.core :as util]
            [clojure.core.async :as async])
  (:import (org.apache.kafka.common.serialization StringSerializer Serializer)
           (org.apache.kafka.clients.producer KafkaProducer ProducerRecord Producer)
           (java.util Map)))

(defn producer [kafka-servers]
  (let [max-message-size (* 10 1024 1024)
        config           {"bootstrap.servers" kafka-servers
                          "max.request.size"  (int max-message-size)}]
    (KafkaProducer. ^Map config
                    ^Serializer (doto (StringSerializer.)
                                  (.configure config true))
                    ^Serializer (doto (StringSerializer.)
                                  (.configure config false)))))

(defn publish [^Producer producer topic key value]
  "Serializers in this example are strings. If not already strings, coercion will be attempted."
  (let [record (if key
                 (ProducerRecord. topic key value)
                 (ProducerRecord. topic value))]
    (log/trace "Publishing to" topic "key" (pr-str key) "value" (pr-str value))
    (.send producer record)))

(defn check-ledger-or-exit [conn fluree-servers fluree-ledger]
  "Verifies ledger specified exists at server(s) specified else exit with message."
  (when (util/exception? (async/<!! (fdb/db conn fluree-ledger)))
    (log/error "Ledger" fluree-ledger "cannot be found at Fluree server(s):" fluree-servers
               ". Please update FLUREE_LEDGER and/or FLUREE_SERVERS environment variables. Exiting.")
    (System/exit 1)))

(defn startup [config]
  "Returns 'system' as a map to "
  (let [{:keys [fluree-servers fluree-ledger kafka-servers kafka-topic]
         :or   {fluree-servers "http://localhost:8090"
                fluree-ledger  "test/ledger"
                kafka-servers  "localhost:9092"
                kafka-topic    "test"}} config
        _              (log/info "Starting up with config: "
                                 {:fluree-servers fluree-servers :fluree-ledger fluree-ledger
                                  :kafka-servers  kafka-servers :kafka-topic kafka-topic})
        conn           (fdb/connect fluree-servers)
        _              (check-ledger-or-exit conn fluree-servers fluree-ledger)
        producer       (producer kafka-servers)
        listener-key   "kafka-1"
        listener-fn    (fn [event data]
                         (log/trace "New Fluree event:" event data)
                         (when (= :block event)
                           (->> (fdb/block-event->map conn fluree-ledger data)
                                (json/encode)
                                (publish producer kafka-topic fluree-ledger))))
        close-listener (fn [] (fdb/close-listener conn fluree-ledger listener-key))]
    (fdb/listen conn fluree-ledger listener-key listener-fn)
    {:producer       producer
     :topic          kafka-topic
     :conn           conn
     :close-listener close-listener
     :closed?        (async/chan)}))

(defn close [system]
  "Shuts down / releases resources"
  (let [{:keys [^Producer producer conn close-listener closed?]} system]
    (log/info "Shutting down!")
    (async/put! closed? :closed)                            ;; formally close out
    (.close producer)                                       ;; close Kafka producer
    (close-listener)                                        ;; close Fluree ledger listener
    (fdb/close conn)                                        ;; close Fluree connection
    (log/info "Shut down complete.")))

(defn wait-until-closed [system]
  "block until explicitly closed to keep process running"
  (async/<!! (:closed? system)))

(defn -main [& args]
  (let [config (util/without-nils
                 {:fluree-servers (System/getenv "FLUREE_SERVERS")
                  :fluree-ledger  (System/getenv "FLUREE_LEDGER")
                  :kafka-servers  (System/getenv "KAFKA_SERVERS")
                  :kafka-topic    (System/getenv "KAFKA_TOPIC")})
        system (startup config)]
    (wait-until-closed system)
    (.addShutdownHook
      (Runtime/getRuntime)
      (Thread. ^Runnable (fn [] (close system))))))
