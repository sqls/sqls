
(ns sqls.jdbc
  "Encapsulate JDBC stuff, expose trivial API.
  All JDBC interaction should be routed via this module
  (or in future through other modules in this namespace)."
  (:require clojure.java.jdbc)
  (:import java.net.URL)
  (:import java.net.URLClassLoader)
  (:import java.sql.Driver)
  (:import java.sql.DriverManager)
  (:import sqls.driver.DriverShim)
)


(defn connect!
  "Create JDBC connection.
  TODO: handle errors.
  "
  [conn-data]
  (let [conn-str (conn-data "jdbc-conn-str")
        conn-class (conn-data "class")
        conn-jar (conn-data "jar")]
    (if (not= conn-jar nil)
      (let [url (java.net.URL. (str "file://" conn-jar))  ; TODO: create correct URL
            url-class-loader (java.net.URLClassLoader. (into-array [url]))
            cls (.loadClass url-class-loader conn-class)]
        (let [orig-driver (cast java.sql.Driver (.newInstance cls)) ; ugly way to create instance
              shim-driver (sqls.driver.DriverShim. orig-driver)]
          (java.sql.DriverManager/registerDriver shim-driver)
        )
      )
      (Class/forName conn-class))
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

