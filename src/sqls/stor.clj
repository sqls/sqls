
(ns sqls.stor
  "Storage support.
  Saving and loading of preferences, connection data, worksheet state etc."
  (:use [clojure.tools.logging :only (debug info)])
  (:require [clojure.data.json :as json])
  (:require [sqls.util :as util])
  (:import [java.io IOException]))


(defn load-settings!
  [conf-dir]
  (try
    (json/read-str (slurp (util/path-join [conf-dir "settings.json"])))
    (catch Exception _ (hash-map))))


(defn write-settings!
  [^String conf-dir
   settings]
  (spit (util/path-join [conf-dir "settings.json"]) (json/write-str settings)))


(defn load-connections!
  "Load connections from connections.json."
  [^String conf-dir]
  (try
    (json/read-str (slurp (util/path-join [conf-dir "connections.json"])))
    (catch Exception _ nil)))


(defn save-connections!
  "Write connections to connections.json"
  [^String conf-dir
   connections]
  (spit (util/path-join [conf-dir "connections.json"]) (json/write-str connections)))


(defn add-connection!
  "Add connection to connection list in conf-dir, return new list.

  Parameters:

  - conn-data is a map with following keys:

    - name - name of the connection,
    - jdbc-conn-str - jdbc connection URI,
    - desc - description,
    - driver - JDBC connection driver name.

  Returns new connection list."
  [^String conf-dir
   conn-data]
  (let [connections (load-connections! conf-dir)
        connections-without-new (remove #(= (% "name") (conn-data "name")) connections)
        keyfn (fn [conn-data] [(conn-data "name")
                               (conn-data "class")
                               (conn-data "desc")])
        connections-with-new (sort-by keyfn (cons conn-data connections-without-new))]
    (save-connections! conf-dir connections-with-new)
    connections-with-new))


(defn delete-connection!
  "Delete connection by name."
  [^String conf-dir
   ^String name]
  (assert (not= name nil))
  (let [connections (load-connections! conf-dir)
        connections-without-deleted (remove #(= (% "name") name) connections)]
    (save-connections! conf-dir connections-without-deleted)
    connections-without-deleted))


(defn load-worksheet-data!
  "Read current worksheet data.
  Takes connection name. For now all connections are worksheets (1-1 relationship) in terms of state.
  This obviously is needs to be designed - either we don't allow many worksheets of one conn,
  or we have many states per conn. Either way it's to be chosen later.

  Returns a map with worksheet data.

  Data file is called \"worksheetdata.json\" and it's for now located in current directory.
  "
  [^String conf-dir
   ^String conn-name]
  (assert (not= conn-name nil))
  (let [worksheet-data-path (util/path-join [conf-dir "worksheetdata.json"])
        datas (try
                (-> (slurp worksheet-data-path)
                    (json/read-str))
                (catch IOException _ nil))]
    (if datas
      (datas conn-name))))


(defn save-worksheet-data!
  "Save worksheet state."
  [^String conf-dir
   ^String conn-name
   worksheet-data]
  (assert (not= conn-name nil))
  (let [worksheet-data-path (util/path-join [conf-dir "worksheetdata.json"])
        current-data (try
                       (-> (slurp worksheet-data-path)
                           (json/read-str))
                       (catch IOException _ {}))
        new-data (assoc current-data conn-name worksheet-data)
        new-data-str (json/write-str new-data)]
    (spit worksheet-data-path new-data-str)))

