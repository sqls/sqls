
(ns sqls.stor
  (:use [clojure.tools.logging :only (debug info)])
  (:require [clojure.data.json :as json]))


(defn load-settings!
  []
  (try
    (json/read-str (slurp "settings.json"))
    (catch Exception e (hash-map))
  ))


(defn write-settings!
  [settings]
  (println "about to save settings:" settings)
  (spit "settings.json" (json/write-str settings))
  )


(defn load-connections!
  "Load connections from connections.json."
  []
  (try
    (json/read-str (slurp "connections.json"))
    (catch Exception e nil)))


(defn save-connections!
  "Write connections to connections.json"
  [connections]
  (spit "connections.json" (json/write-str connections)))


(defn add-connection!
  "Add connection to connection list.
  
  Parameters:
  
  - conn-data is a map with following keys:
  
    - name - name of the connection,
    - jdbc-conn-str - jdbc connection URI,
    - desc - description,
    - driver - JDBC connection driver name.
  
  Returns new connection list.
  "
  [conn-data]
  (let [connections (load-connections!)
        connections-without-new (remove #(= (% "name") (conn-data "name")) connections)
        keyfn (fn [conn-data] [(conn-data "name")
                               (conn-data "class")
                               (conn-data "desc")])
        connections-with-new (sort-by keyfn (cons conn-data connections-without-new))]
    (println "connections-with-new:" connections-with-new)
    (save-connections! connections-with-new)
    connections-with-new))


(defn delete-connection!
  "Delete connection by name."
  [name]
  (let [connections (load-connections!)
        connections-without-deleted (remove #(= (% "name") name) connections)]
    (save-connections! connections-without-deleted)
    connections-without-deleted))






