(ns sqls.plugin.sqlite
  "Adds support for SQlite databases."
  (:require [sqls.plugin :refer [DatabaseDriverPlugin]]))


(def sqlite-plugin
  (reify DatabaseDriverPlugin
    (classes
      [_]
      [["org.sqlite.JDBC" "SQLite"]])))
