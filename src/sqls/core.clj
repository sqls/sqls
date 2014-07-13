(ns sqls.core
  (:gen-class)
  (:use seesaw.core)
  (:use sqls.ui)
  (:require [clojure.java.io :as io])
  (:require sqls.ui.conn-list)
  (:require sqls.ui.conn-edit)
  (:require [sqls.stor :as stor])
  (:require sqls.jdbc)
  (:require sqls.worksheet)
  (:require [sqls.ui :as ui])
  (:import java.sql.Connection))


(defn save-conn!
  "Save connection.

  Parameters:

  - conn-list-frame - connection list frame,
  - old-conn-data - old connection data if editing, nil if saving new connection,
  - new-conn-data - new connection data to be stored in settings and displayed in frame.
  "
  [conn-list-frame old-conn-data conn-data]
  (let [new-connections (stor/add-connection! conn-data)
        conn-list-table (select conn-list-frame [:#conn-list-table])]
    (config! conn-list-table :model (sqls.ui.conn-list/build-connection-list-model new-connections))))


(defn test-conn!
  "Test connection. Return a map of:

  - :ok - boolean, true if connection was successful,
  - :desc - optional message.
  "
  [conn-data]
  (try
    (let [conn-info (sqls.jdbc/connect! conn-data)
          ^Connection conn (conn-info :conn)
          ^String err-msg (conn-info :msg)
          ^String err-desc (conn-info :desc)]
      (if (not= conn nil)
        (sqls.jdbc/close! conn))
      (if (not= conn nil)
        {:ok true :desc nil}
        {:ok false :desc (format "%s\n%s" err-msg err-desc)}
      ))
    (catch Exception e
      (.printStackTrace e)
      {:ok false :desc (str e)})))


(defn create-worksheet-handlers
  "Create and return a map of functions that perform various actions initiated at worksheet
  that change state outside of worksheet.
  This is because we don't want worksheet code to access other submodules directly."
  [conn-data]
  (println (format "conn-data: %s, conn-data name: %s" conn-data (conn-data "name")))
  (let [conn-name (conn-data "name")]
    (assert (not= conn-name nil))
    {:save-worksheet-data (fn
                            [worksheet-data]
                            (stor/save-worksheet-data! conn-name worksheet-data))}))


(defn -sqls
  "Run sqls, but don't exit unless exit-on-close? is set. Useful for running in repl."
  ([] (-sqls false))
  (
    [exit-on-close?]
    (native!)
    (invoke-later
      (let [settings (stor/load-settings!)
            connections (stor/load-connections!)
            handlers {:create-worksheet (fn [conn-data]
                                          (let [conn-name (conn-data "name")
                                                worksheet-data (stor/load-worksheet-data! conn-name)
                                                contents (if worksheet-data (worksheet-data "contents"))]
                                            (println (format "creating worksheet with contents: %s" contents))
                                            (sqls.worksheet/create-and-show-worksheet! conn-data contents (create-worksheet-handlers conn-data))))
                      :save-conn save-conn!
                      :test-conn test-conn!
                      :delete-connection! stor/delete-connection!
                      :about (fn
                               []
                               (-> (io/resource "about.txt")
                                   (slurp)
                                   (ui/show-about-dialog!)))}
            conn-list-frame (sqls.ui.conn-list/create-login-ui exit-on-close? handlers settings connections)]
        (pack! conn-list-frame)
        (show! conn-list-frame)))))


(defn -main
  [& args]
  (-sqls true))


