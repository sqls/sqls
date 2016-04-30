(ns sqls.plugin.sqlite
  "Adds support for SQlite databases.")

(defn classes
  []
  [["org.sqlite.JDBC" "SQLite"]])

(def sqlite-plugin
  {:name "SQLite"
   :classes classes
   :jdbc-url-template "jdbc:sqlite:<filename>"})

