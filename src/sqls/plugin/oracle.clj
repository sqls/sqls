(ns sqls.plugin.oracle
  "Oracle plugin for SQLs"
  (:require [clojure.string :as s]
            [clojure.java.jdbc :as j]
            [sqls.util :refer [format-tabular]])
  (:import [java.sql Connection]))

(defn classes
  []
  [["oracle.jdbc.OracleDriver" "Oracle"]])

(defn orcl-describe-object!
  [conn object-name]
  {:pre [(instance? Connection conn)
         (string? object-name)]}
  (let [table-info (-> (j/query {:connection conn}
                                ["select table_name, tablespace_name
                                  from user_tables
                                  where table_name = ?"
                                 (s/upper-case object-name)])
                       first)
        columns (when table-info
                  (j/query {:connection conn}
                           ["select column_name, data_type
                             from user_tab_columns
                             where table_name = ?
                             order by column_id"
                            (:table_name table-info)]))]
    (when table-info
      (let [cols-desc (->> columns
                           (map-indexed (fn [i c] [(str (inc i) ".")
                                                   (:column_name c)
                                                   (:data_type c)]))
                           format-tabular
                           (s/join "\n"))]
        (str "Table: " (:table_name table-info) "\n"
             "Columns:\n"
             cols-desc)))))

(def oracle-plugin
  {:name "Oracle"
   :classes classes
   :jdbc-url-template "jdbc:oracle:thin:<username>/<password>@<host>:<port>:<db>"
   :describe-object orcl-describe-object!})
