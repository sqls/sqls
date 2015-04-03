(ns sqls.plugin.psql
  "Adds support for PostgreSQL databases."
  (:require [sqls.plugin :refer [DatabaseDriverPlugin]]))


(def psql-plugin
  (reify DatabaseDriverPlugin
    (classes
      [_]
      [["org.postgresql.Driver" "PostgreSQL"]])))