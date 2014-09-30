(ns sqls.core
  (:gen-class)
  (:use seesaw.core)
  (:use sqls.ui)
  (:require [clojure.java.io :as io]
            [clojure.string :as string])
  (:require [clojure.tools.cli :refer [parse-opts]])
  (:require sqls.ui.conn-list)
  (:require sqls.ui.conn-edit)
  (:require [sqls.stor :as stor]
            [sqls.conf :as conf])
  (:require sqls.jdbc)
  (:require sqls.worksheet)
  (:require [sqls.ui :as ui])
  (:import java.sql.Connection))


(def cli-options
  [["-c" "--conf-dir" "Config dir"]
   ["-h" "--help"]])


(defn usage
  [summary]
  (->> ["sqls is a SQL developer and admin UI tool"
        summary]
       (string/join \newline)))


(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))


(defn exit [status msg]
  (println msg)
  (System/exit status))


(defn save-conn!
  "Save connection.

  Parameters:

  - conf-dir,
  - conn-list-frame - connection list frame,
  - old-conn-data - old connection data if editing, nil if saving new connection,
  - new-conn-data - new connection data to be stored in settings and displayed in frame.
  "
  [conf-dir conn-list-frame _old-conn-data conn-data]
  (let [new-connections (stor/add-connection! conf-dir conn-data)
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
  [conf-dir conn-data]
  (let [conn-name (conn-data "name")]
    (assert (not= conn-name nil))
    {:save-worksheet-data (fn
                            [worksheet-data]
                            (stor/save-worksheet-data! conf-dir conn-name worksheet-data))}))


(defn -sqls
  "Run sqls, but don't exit unless exit-on-close? is set. Useful for running in repl.

  Params:

  - exit-on-close? - should we call exit when main window is closed? default: true,
  - cli-conf-dir - conf directory to use instead of automatically found, can be specified
    on command line.
  "
  ([] (-sqls true))
  ([exit-on-close?] (-sqls exit-on-close? nil))
  (
    [exit-on-close?
     ^String cli-conf-dir]
    (native!)
    (invoke-later
      (let [cwd (System/getProperty "user.dir")
            conf-dir (if cli-conf-dir cli-conf-dir (conf/find-conf-dir cwd))
            _ (println (str "conf dir: " conf-dir))
            _ (assert conf-dir)
            _ (conf/ensure-conf-dir! conf-dir)
            settings (stor/load-settings! conf-dir)
            connections (stor/load-connections! conf-dir)
            handlers {:create-worksheet (fn [conn-data]
                                          (let [conn-name (conn-data "name")
                                                worksheet-data (stor/load-worksheet-data! conf-dir conn-name)
                                                contents (if worksheet-data (worksheet-data "contents"))]
                                            (sqls.worksheet/create-and-show-worksheet!
                                              conn-data
                                              contents
                                              (create-worksheet-handlers conf-dir conn-data))))
                      :save-conn (partial save-conn! conf-dir)
                      :test-conn test-conn!
                      :delete-connection! (partial stor/delete-connection! conf-dir)
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
  (let [{:keys [options errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options) (exit 0 (usage summary))
      errors (exit 1 (error-msg errors)))
    (-sqls true)))


