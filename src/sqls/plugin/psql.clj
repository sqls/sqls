(ns sqls.plugin.psql
  "Adds support for PostgreSQL databases.")

(defn classes
  []
  [["org.postgresql.Driver" "PostgreSQL"]])


(def psql-plugin
  {:classes classes
   :jdbc-url-template "jdbc:postgresql://<host>:<port>/<database>?user=<user>&password=<password>"})
