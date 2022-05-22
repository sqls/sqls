(ns sqls.jdbc
  "Encapsulate JDBC stuff, expose trivial API.
  All JDBC interaction should be routed via this ns."
  (:require [clojure.string :refer [blank?]])
  (:require clojure.java.jdbc)
  (:require fipp.edn)
  (:require [sqls.util :as util])
  (:import [java.net URL URLClassLoader]
           [java.security PrivilegedActionException]
           [java.sql Connection Driver DriverManager SQLException]
           [javax.sql DataSource]
           [com.mchange.v2.c3p0 DataSources]
           [sqls.driver DriverShim]
           (java.io StringWriter PrintWriter)))

(defn connect-without-jar!
  "Connect using builtin classpath.
  TODO: return either conn or error desc."
  [conn-data]
  (let [conn-class (:class conn-data)
        conn-str (:conn conn-data)]
    (if (not (empty conn-class))
      (try
        (Class/forName conn-class)
        (catch ClassNotFoundException _ nil)))
    (try
      (clojure.java.jdbc/get-connection {:connection-uri conn-str})
      (catch SQLException _ nil)
      (catch PrivilegedActionException _ nil))))

(defn connect-with-urls!
  "Try to connect using URLClassLoader initialized with given url.

  Parameters:

  - conn-data - defines connection (class, jdbc url),
  - urls - nonempty coll of urls.

  Returns JDBC connection.

  TODO: return either JDBC connection or error info."
  [conn-data urls]
  {:pre [(string? (:class conn-data))
         (string? (:conn conn-data))
         (not (nil? urls))
         (every? (partial instance? URL) urls)
         (pos? (count urls))]}
  ; (println (format "connect-with-urls: urls:\n%s" (with-out-str (fipp.edn/pprint urls))))
  (let [conn-class (:class conn-data)
        conn-str (:conn conn-data)
        url-class-loader (URLClassLoader. (into-array urls))
        cls (try
              (.loadClass url-class-loader conn-class)
              (catch ClassNotFoundException e
                (println (format "can't load with urls: %s" e))
                nil))]
    (when cls
      (let [orig-driver (cast Driver (.newInstance cls)) ; ugly way to create instance
            shim-driver (DriverShim. orig-driver)]
        (DriverManager/registerDriver shim-driver)
        (clojure.java.jdbc/get-connection {:connection-uri conn-str})))))

(defn connect-with-absolute-path!
  "Connect with absolute path."
  [conn-data
   ^String path]
  (let [url (URL. "file" "" path)]
    (connect-with-urls! conn-data [url])))

(defn connect-with-path!
  "Connect using some path.

  Path is unix-style path (with slashes).
  It is an absolute path if it starts with slash.
  It is a relative path otherwise."
  [conn-data]
  (let [path (:jar conn-data)
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
    (if (> (count jar-urls) 0)
      (connect-with-urls! conn-data jar-urls)
      nil)))

(defn connect-with-auto!
  "Connect with current classpath and if this fails, then search for jars
  in default locations and try to connect with them."
  [conn-data]
  (let [conn (connect-without-jar! conn-data)]
    (if (not= conn nil)
      conn
      (connect-with-auto-jars! conn-data))))

(defn exception-to-stacktrace
  "Pretty text from exception stack trace."
  ^String
  [^Throwable e]
  (let [w (StringWriter.)
        pw (PrintWriter. w)]
    (.printStackTrace e pw)
    (let [^String st (str w)]
      st)))

(defn connect!
  "Create JDBC connection.

  Two ways: we either have jar (which is a path, either absolute path starting with slash, or
  relative path) or we have no specific jar given.

  If jar is not given, then we:

  1. try to just load driver,
  2. otherwise find all jar files in various locations, and try to load given
     class from each and every jar file found.

  Returns hash map of three elements (nil elements are optional):

  - conn - JDBC conn, nil if could not connect,
  - msg - short error message, nil if connected,
  - desc - nil if connected, otherwise optionally verbose error description."
  [conn-data]
  {:pre [(not (nil? conn-data))
         (not (nil? (:name conn-data)))
         (not (nil? (:class conn-data)))
         (not (nil? (:conn conn-data)))]}
  (let [^String conn-str (:conn conn-data)
        ^String conn-class (:class conn-data)
        ^String conn-jar (:jar conn-data)]
    (try
      (let [^Connection conn (if (not (blank? conn-jar))
                                        (connect-with-path! conn-data)
                                        (connect-with-auto! conn-data))]
        (if (not= conn nil)
          (do
            (.setAutoCommit conn false)
            {:conn conn})
          {:conn nil
           :msg "Connection failed"}))
      (catch PrivilegedActionException e {:conn nil
                                          :msg  (str e)
                                          :desc (exception-to-stacktrace e)})
      (catch SQLException e {:conn nil
                             :msg  (str e)
                             :desc (exception-to-stacktrace e)}))))

(defn create-unpooled-data-source!
  [conn-data]
  (reify
    DataSource
    (getConnection [this]
      (let [res (connect! conn-data)]
        (if (:conn res)
          (:conn res)
          (throw (Exception. (format "Error connecting: %s:\n%s" (:msg res) (:desc res)))))))))

(defn create-pooled-data-source!
  [conn-data]
  (let [unpooled-data-source (create-unpooled-data-source! conn-data)
        props {"acquireRetryAttempts" "0"
               "initialPoolSize" "0"
               "maxConnectionAge" (str (* 30 60))  ; 30 minutes
               "maxIdleTime" (str (* 5 60 ))  ; 5 minutes
               "maxPoolSize" "4"
               "minPoolSize" "0"}]
    (DataSources/pooledDataSource unpooled-data-source props)))

(defn close!
  "Close connection."
  [^Connection conn]
  (assert (not= conn nil))
  (.close conn))

(defn execute!
  "Execute SQL on connection.
  This function should accept both row-returning and non-row-returning statements.
  This functions returns a cursor of query results or nil if this SQL command does not
  return rows."
  [^Connection conn
   ^String sql]
  (assert (not= conn nil))
  (let [stmt (clojure.java.jdbc/prepare-statement
               conn
               sql
               {
                :result-type :forward-only
                :concurrency :read-only
                :fetch-size 128})
        _ (.setFetchSize stmt 128)
        has-result-set (.execute stmt)]
    (if has-result-set
      (let [result-set (.getResultSet stmt)]
        (.setFetchSize result-set 128)
        (clojure.java.jdbc/result-set-seq result-set {:as-arrays? true}))
      nil)))
