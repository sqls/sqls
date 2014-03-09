
(ns sqls.jdbc
  "Encapsulate JDBC stuff, expose trivial API.
  All JDBC interaction should be routed via this module
  (or in future through other modules in this namespace)."
  (:use [clojure.string :only [blank?]])

  (:require clojure.java.jdbc)
  (:require [sqls.util :as util])

  (:import java.net.URL)
  (:import java.net.URLClassLoader)
  (:import java.security.PrivilegedActionException)
  (:import java.sql.Driver)
  (:import java.sql.DriverManager)
  (:import java.sql.SQLException)

  (:import sqls.driver.DriverShim)
)


(defn connect-without-jar!
  "Connect using builtin classpath."
  [conn-data]
  (let [conn-class (conn-data "class")
        conn-str (conn-data "jdbc-conn-str")]
    (if (not (empty conn-class))
      (try
        (Class/forName conn-class)
        (catch ClassNotFoundException _ nil)))
    (try
      (clojure.java.jdbc/get-connection {:connection-uri conn-str})
      (catch java.sql.SQLException _ nil)
      (catch java.security.PrivilegedActionException _ nil))))


(defn connect-with-urls!
  "Try to connect using URLClassLoader initialized with given url."
  [conn-data urls]
  (let [conn-class (conn-data "class")
        conn-str (conn-data "jdbc-conn-str")
        url-class-loader (java.net.URLClassLoader. (into-array urls))
        _ (println "loader" url-class-loader)
        cls (.loadClass url-class-loader conn-class)]
    (let [orig-driver (cast java.sql.Driver (.newInstance cls)) ; ugly way to create instance
          shim-driver (sqls.driver.DriverShim. orig-driver)]
      (java.sql.DriverManager/registerDriver shim-driver)
      (clojure.java.jdbc/get-connection {:connection-uri conn-str}))))


(defn connect-with-absolute-path!
  "Connect with absolute path."
  [conn-data path]
  (let [url (java.net.URL. "file" "" path)]
    (connect-with-urls! conn-data [url])))


(defn connect-with-path!
  "Connect using some path.

  Path is unix-style path (with slashes).
  It's absolute path if it starts with slash.
  It's relative path otherwise."
  [conn-data]
  (let [path (conn-data "jar")
        absolute-path (util/path-to-absolute-path path)]
    (connect-with-absolute-path! conn-data absolute-path)))


(defn connect-with-auto-jars!
  "Connect using classloader pointing to all jars found in some predefined locations.

  Locations are:

  - . (current directory),
  - ./lib,
  - $HOME/.sqls,
  - $HOME/.sqls/lib,
  - possibly more to be added later ('Library/Application Support' on OS X etc)."
  [conn-data]
  (let [jar-paths (util/find-driver-jars)
        jar-urls (map (fn [p] (java.net.URL. "file" "" p)) jar-paths)]
    (connect-with-urls! conn-data jar-urls)))


(defn connect-with-auto!
  "Connect with current classpath and if this fails, then search for jars
  in default locations and try to connect with them."
  [conn-data]
  (let [conn (connect-without-jar! conn-data)]
    (if (not= conn nil)
      conn
      (connect-with-auto-jars! conn-data))))


(defn connect!
  "Create JDBC connection.

  Two ways: we either have jar (which is a path, either absolute path starting with slash, or
  relative path) or we have no specific jar given.

  If jar is given, then we should first locate this jar and then use this jar to get connection.

  If jar is not given, then we:

  1. try to just load driver,
  2. otherwise find all jar files in various locations,
     and try to load given class from each and every jar file found.

  TODO: handle errors.
  "
  [conn-data]
  (let [conn-str (conn-data "jdbc-conn-str")
        conn-class (conn-data "class")
        conn-jar (conn-data "jar")]
    (if (not (blank? conn-jar))
      (connect-with-path! conn-data)
      (connect-with-auto! conn-data))))


(defn close!
  "Close connection."
  [conn]
  (assert (not= conn nil))
  (.close conn))


(defn execute!
  "Execute SQL on connection.
  This function should accept both row-returning and non-row-returning statements
  (that this distinction is different than DML vs SELECT statements,
  as some DML statements return rows).

  This functions returns a cursor of query results or nil if this SQL command does not
  return rows."
  [conn sql]
  (assert (not= conn nil))
  (let [stmt (clojure.java.jdbc/prepare-statement conn sql)
        has-result-set (.execute stmt)]
    (if has-result-set
      (clojure.java.jdbc/result-set-seq (.getResultSet stmt) :as-arrays? true)
      nil)))
