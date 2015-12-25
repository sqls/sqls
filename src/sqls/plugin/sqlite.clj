(ns sqls.plugin.sqlite
  "Adds support for SQlite databases.")

(defn classes
  []
  [["org.sqlite.JDBC" "SQLite"]])

(def sqlite-plugin
  {:classes classes
   :jdbc-url-template "jdbc:sqlite:<filename>"})

