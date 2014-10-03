(ns sqls.core
  (:gen-class)
  (:use seesaw.core)
  (:use sqls.ui)
  (:require [clojure.java.io :as io]
            [clojure.string :as string])
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :refer [spy spyf]])
  (:require sqls.ui.conn-list)
  (:require sqls.ui.conn-edit)
  (:require [sqls.stor :as stor]
            [sqls.conf :as conf])
  (:require sqls.jdbc)
  (:require sqls.worksheet)
  (:require [sqls.ui :as ui])
  (:import java.sql.Connection
           [clojure.lang Atom]))


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


(defn create-sqls-atom!
  "Create central data structure for SQLs.
  This is an Atom, since it's shared mutable data structure (shared between all worksheets).
  It's required because we want stuff like explicit JVM stop when closing last frame (which requires

  SQLs atom is for now map and it contains following keys:

  - :worksheets - map of worksheets, by conn name,
  - :conf-dir - current value of conf-dir, configuration files go there.
  "
  [conf-dir]
  (let [s {:worksheets {}
           :conf-dir   conf-dir}]
    (atom s)))


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
  [^Atom sqls-atom
   conn-data]
  (assert sqls-atom)
  (let [conf-dir (:conf-dir @sqls-atom)
        conn-name (conn-data "name")]
    (assert (not= conn-name nil))
    {:save-worksheet-data (fn
                            [worksheet-data]
                            (assert (map? worksheet-data))
                            (stor/save-worksheet-data! conf-dir conn-name worksheet-data))
     :remove-worksheet-from-sqls (fn
                                   [conn-name]
                                   (swap! sqls-atom (fn
                                                      [s]
                                                      (let [worksheets (:worksheets s)
                                                            new-worksheets (dissoc worksheets conn-name)
                                                            new-sqls (assoc s :worksheets new-worksheets)]
                                                        (spyf "removed worksheet from sqls-atom and have: %s" new-sqls)))))}))


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
            _ (assert conf-dir)
            sqls-atom (create-sqls-atom! conf-dir)
            _ (conf/ensure-conf-dir! conf-dir)
            settings (stor/load-settings! conf-dir)
            connections (stor/load-connections! conf-dir)
            handlers {:create-worksheet (fn [conn-data]
                                          (let [conn-name (conn-data "name")
                                                is-open? (not= nil ((:worksheets @sqls-atom) conn-name))
                                                _ (assert (not is-open?))
                                                worksheet-data (stor/load-worksheet-data! conf-dir conn-name)
                                                worksheet (sqls.worksheet/create-and-show-worksheet!
                                                            conn-data
                                                            worksheet-data
                                                            (create-worksheet-handlers sqls-atom conn-data))]
                                            (swap! sqls-atom (fn
                                                               [s]
                                                               ; assoc-in...
                                                               (let [old-worksheets (:worksheets s)
                                                                     new-worksheets (assoc old-worksheets conn-name worksheet)]
                                                                 (assoc s :worksheets new-worksheets))))))
                      :is-connectable? (fn
                                         [conn-name]
                                         (let [worksheets (:worksheets @sqls-atom)
                                               _ (assert (not= nil worksheets))]
                                           (not (contains? worksheets conn-name))
                                           ))
                      :save-conn (partial save-conn! conf-dir)
                      :test-conn test-conn!
                      :delete-connection! (partial stor/delete-connection! conf-dir)
                      :about (fn
                               []
                               (-> (io/resource "about.txt")
                                   (slurp)
                                   (ui/show-about-dialog!)))}
            conn-list-frame (sqls.ui.conn-list/create-login-ui sqls-atom exit-on-close? handlers settings connections)]
        (pack! conn-list-frame)
        (show! conn-list-frame)))))


(defn -main
  [& args]
  (let [{:keys [options errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options) (exit 0 (usage summary))
      errors (exit 1 (error-msg errors)))
    (-sqls true)))


