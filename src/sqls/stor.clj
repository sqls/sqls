
(ns sqls.stor
  (:use [clojure.tools.logging :only (debug info)])
  (require [clojure.data [json :as json]]))


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


(defn add-connection-to-settings
  "Add connection to settings.
  Settings is map which contains :connections.
  :connections is a map of name to connection data."
  [settings conn]
  (let [old-connections (settings "connections")
        new-connections (assoc old-connections (conn "name") conn)
        new-settings (assoc settings "connections" new-connections)]
    new-settings))


(defn add-connection!
  [name jdbc-conn-str desc]
  (let [settings (load-settings!)
        new-conn {"name" name "jdbc-conn-str" jdbc-conn-str "desc" desc}
        new-settings (add-connection-to-settings settings new-conn)]
    (write-settings! new-settings)
    (new-settings "connections")
    )
  )

