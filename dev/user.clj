(ns user
  (:require [fluree.kafka.core :as fkafka]
            [fluree.db.api :as fdb]))


(comment

  (def system (fkafka/startup {:fluree-servers "http://localhost:8090"
                               :fluree-ledger  "one-donation/production"
                               :kafka-servers  "localhost:9092"
                               :kafka-topic    "test"}))


  (fkafka/close system)

  (def conn (fdb/connect "http://localhost:8090"))

  )
