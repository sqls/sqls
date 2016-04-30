(ns sqls.plugin.oracle
  "Oracle plugin for SQLs")

(defn classes
  []
  [["oracle.jdbc.OracleDriver" "Oracle"]])

(def oracle-plugin
  {:name "Oracle"
   :classes classes
   :jdbc-url-template "jdbc:oracle:thin:<username>/<password>@<host>:<port>:<db>"})