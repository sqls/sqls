(ns sqls.plugin.psql
  "Adds support for PostgreSQL databases."
  (:require [clojure.string :as s]
            [clojure.java.jdbc :as j]
            [sqls.util :refer [debugf format-tabular]]))

(defn classes
  []
  [["org.postgresql.Driver" "PostgreSQL"]])

(defn psql-describe-object!
  "Try to find useful info about given object."
  [conn object-name]
  (debugf "object-name: %s" object-name)
  (let [table-info (-> (j/query {:connection conn}
                                ["select table_catalog, table_schema, table_name
                                 from information_schema.tables
                                 where table_name = ?
                                 limit 1"
                                 object-name])
                       first)
        columns (when table-info
                  (j/query {:connection conn}
                           ["select
                                column_name,
                                data_type
                            from information_schema.columns
                            where
                              table_catalog = ?
                              and table_schema = ?
                              and table_name = ?
                            order by ordinal_position"
                            (:table_catalog table-info)
                            (:table_schema table-info)
                            (:table_name table-info)]))]
    (when table-info
      (format "Table: %s.%s.%s\nColumns:\n%s\n"
              (:table_catalog table-info)
              (:table_schema table-info)
              (:table_name table-info)
              (s/join "\n"
                      (format-tabular
                        (map-indexed (fn [i column]
                                       [(str (inc i) ".")
                                        (:column_name column)
                                        (:data_type column)])
                                     columns)))))))

(def psql-plugin
  {:name "PostgreSQL"
   :classes classes
   :jdbc-url-template "jdbc:postgresql://<host>:<port>/<database>?user=<user>&password=<password>"
   :describe-object psql-describe-object!})
