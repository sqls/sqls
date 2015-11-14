(ns sqls.core
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.cli :as cli])
  (:require sqls.jdbc
            io.aviso.logging
            io.aviso.repl
            sqls.worksheet
            [sqls.conf :as conf]
            [sqls.plugin :refer [DatabaseDriverPlugin classes]]
            [sqls.plugin.psql :refer [psql-plugin]]
            [sqls.plugin.sqlite :refer [sqlite-plugin]]
            [sqls.ui.seesaw :refer [create-ui!]]
            [sqls.stor :as stor]
            [sqls.ui.proto :refer [ConnListWindow
                                   UI
                                   invoke-later!
                                   show-conn-list-window!
                                   create-conn-list-window!
                                   set-conns!
                                   show-about!]]
            [sqls.util :refer [debugf info infof warnf]])
  (:import [java.sql Connection]
           [clojure.lang Atom]
           [sqls.model Conn]))


(def cli-options
  [["-c" "--conf-dir" "Config dir"]
   ["-h" "--help"]])


(def builtin-plugins
  [psql-plugin
   sqlite-plugin])


(defn usage
  [summary]
  (->> ["sqls is a SQL developer and admin UI tool"
        summary]
       (string/join \newline)))


(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))


(defn exit [status msg]
  (info msg)
  (System/exit status))


(defn create-sqls-atom!
  "Create central data structure for SQLs.
  This is an Atom, since it's shared mutable data structure (shared between all worksheets).
  It's required because we want stuff like explicit JVM stop when closing last frame (which requires

  SQLs atom is for now map and it contains following keys:

  - :worksheets - map of worksheets, by conn name,
  - :conn-list - truthy if conn-list is on screen,
  - :conf-dir - current value of conf-dir, configuration files go there."
  [conf-dir]
  (let [s {:worksheets {}
           :conf-dir   conf-dir
           :conn-list  false}]
    (atom s)))


(defn save-conn!
  "Save connection.
  Parameters:
  - conf-dir,
  - conn-list-win - connection list frame (something that satisfies conn list proto),
  - old-conn-data - old connection data if editing, nil if saving new connection,
  - new-conn-data - new connection data to be stored in settings and displayed in frame.
  "
  [conf-dir conn-list-win _old-conn-data conn-data]
  (let [new-connections (stor/add-connection! conf-dir conn-data)]
    (set-conns! conn-list-win new-connections)))


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
      (infof "Exception when testing connection: %s" e)
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
                                                        (infof "removed worksheet from sqls-atom and have: %s" new-sqls)
                                                        new-sqls))))}))


(defn get-plugins-drivers
  "Ask plugins for drivers"
  [plugins]
  {:pre [(or
           (nil? plugins)
           (and
             (sequential? plugins)
             (not (empty? plugins))))]
   :post [(fn [y]
            (or (nil? y)
                (and (seq? y)
                     (not (empty? y))
                     (every? string? y))))]}
   (apply concat (map classes plugins)))


(defn get-plugins-conns-drivers
  [plugins conns]
  {:post [sequential?]}
  (let [plugin-drivers (get-plugins-drivers plugins)
        class-names (into {} plugin-drivers)
        plugin-classes (map first plugin-drivers)
        conn-classes (map #(get % "class") conns)
        all-classes (sort (into #{} (concat plugin-classes conn-classes)))]
    (map (fn [c] [c (get class-names c)]) all-classes)))


(defn maybe-exit!
  [exit-on-close? sqls-atom]
  {:pre [(-> sqls-atom deref :worksheets map?)]}
  (let [s @sqls-atom]
    (when (and exit-on-close?
               (empty? (:worksheets s))
               (not (:conn-list s)))
      (println "bye")
      (System/exit 0))))

(defn on-conn-list-closed!
  [exit-on-close? sqls-atom]
  (println (format "on-conn-list-closed"))
  (swap! sqls-atom assoc :conn-list false)
  (when exit-on-close?
    (maybe-exit! exit-on-close? sqls-atom)))

(defn -sqls
  "Run sqls, but don't exit unless exit-on-close? is set. Useful for running in repl.

  Params:

  - exit-on-close? - should we call exit when main window is closed? default: true,
  - cli-conf-dir - conf directory to use instead of automatically found, can be specified
    on command line.
  "
  ([] (-sqls true))
  ([exit-on-close?] (-sqls exit-on-close? nil))
  ([exit-on-close?
   ^String cli-conf-dir]
   (io.aviso.repl/install-pretty-exceptions)
   (infof "-sqls %s %s" exit-on-close? cli-conf-dir)
   (let [about-text (io/resource "about.txt")
         ^UI ui (create-ui! about-text {})]
     (println (format "created ui: %s" ui))
     (let [cwd (System/getProperty "user.dir")
           conf-dir (if cli-conf-dir cli-conf-dir (conf/find-conf-dir cwd))
           _ (infof "conf-dir: %s" conf-dir)
           _ (assert conf-dir)
           sqls-atom (create-sqls-atom! conf-dir) ; sqls-atom holds high level mutable state of sqls
           _ (conf/ensure-conf-dir! conf-dir)
           settings (stor/load-settings! conf-dir)
           connections-atom (->> (stor/load-connections! conf-dir)
                                 (map (fn [c] [(:name c) c]))
                                 (apply concat)
                                 (apply hash-map)
                                 atom)
           drivers (get-plugins-conns-drivers builtin-plugins @connections-atom)
           handlers {:create-worksheet (fn [conn-name]
                                         (assert (string? conn-name))
                                         (println (format "trying to create worksheet for %s" conn-name))
                                         (let [^Conn conn-data (@connections-atom conn-name)]
                                           (if (contains? (:worksheets @sqls-atom) conn-name)
                                             (warnf "worksheet already present in sqls state")
                                             (let [worksheet-data (stor/load-worksheet-data! conf-dir conn-name)
                                                   worksheet-handlers {:save-worksheet-data (fn [data]
                                                                                              (println (format "save data for worksheet %s" conn-name)))
                                                                       :worksheet-closed (fn [conn-name]
                                                                                           (println (format "worksheet %s closed" conn-name))
                                                                                           (swap! sqls-atom update :worksheets dissoc conn-name)
                                                                                           (maybe-exit! exit-on-close? sqls-atom))}
                                                   worksheet (sqls.worksheet/create-and-show-worksheet! ui conn-data worksheet-data worksheet-handlers)]
                                               (swap! sqls-atom assoc-in [:worksheets conn-name] worksheet)))))
                     :save-conn (partial save-conn! conf-dir)
                     :test-conn test-conn!
                     :delete-connection! (partial stor/delete-connection! conf-dir)
                     :conn-list-closed (partial on-conn-list-closed! exit-on-close? sqls-atom)}
           ^ConnListWindow conn-list-window (create-conn-list-window! ui drivers handlers (->> @connections-atom
                                                                                               vals
                                                                                               (sort-by :name)))]
       (println (format "conn-list-window: %s" conn-list-window))
       (swap! sqls-atom assoc :conn-list true)
       (show-conn-list-window! conn-list-window)))))


(defn -main
  [& args]
  (let [{:keys [options errors summary]} (cli/parse-opts args cli-options)]
    (cond
      (:help options) (exit 0 (usage summary))
      errors (exit 1 (error-msg errors)))
    (-sqls true)))


