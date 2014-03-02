
(ns sqls.jdbc
  "Encapsulate JDBC stuff, expose trivial API.
  All JDBC interaction should be routed via this module
  (or in future through other modules in this namespace)."
  (:require clojure.java.jdbc))


(defn connect!
  "Create JDBC connection.
  TODO: handle errors.
  "
  [conn-data]
  (let [conn-str (conn-data "jdbc-conn-str")
        conn-class (conn-data "class")]
    (let [driver-class (Class/forName conn-class)] (println "driver:" driver-class))
    (clojure.java.jdbc/get-connection {:connection-uri conn-str})))


(defn execute!
  "Execute SQL on connection.
  This function should accept both row-returning and non-row-returning statements
  (that this distinction is different than DML vs SELECT statements,
  as some DML statements return rows).
  
  This functions returns a cursor of query results or nil if this SQL command does not
  return rows."
  [conn sql]
  (println "about to execute sql on connection")
  (println "conn:" conn)
  (println "sql:" sql)
  (let [stmt (clojure.java.jdbc/prepare-statement conn sql)
        has-result-set (.execute stmt)]
    (if has-result-set
      (clojure.java.jdbc/result-set-seq (.getResultSet stmt) :as-arrays? true)
      nil)))

