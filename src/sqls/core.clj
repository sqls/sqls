(ns sqls.core
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.cli :as cli])
  (:require sqls.jdbc
            io.aviso.logging
            io.aviso.repl
            fipp.edn
            sqls.worksheet
            [sqls.conf :as conf]
            [sqls.plugin]
            [sqls.plugin.oracle :refer [oracle-plugin]]
            [sqls.plugin.psql :refer [psql-plugin]]
            [sqls.plugin.sqlite :refer [sqlite-plugin]]
            [sqls.ui.seesaw :refer [create-ui!]]
            [sqls.stor :as stor]
            [sqls.ui.proto :refer [ConnListWindow
                                   UI
                                   add-conn!
                                   enable-conn!
                                   disable-conn!
                                   invoke-later!
                                   show-conn-list-window!
                                   create-conn-list-window!
                                   create-timer!
                                   destroy-timer!
                                   show-about!]]
            [sqls.util :refer [debugf get-rss! human-readable-size info infof warnf]])
  (:import [java.sql Connection]
           [sqls.model Conn]))


(def cli-options
  [["-c" "--conf-dir" "Config dir"]
   ["-h" "--help"]])

(def builtin-plugins
  [oracle-plugin
   psql-plugin
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
  It's required because we want stuff like explicit JVM stop when closing last frame.

  SQLs atom is map and it contains following keys:

  - :timer - UI timer,
  - :plugins - plugins,
  - :worksheets - map of worksheets, by conn name,
  - :conn-list - conn-list window or nil,
  - :connections - map of conn name to Conn record,
  - :conf-dir - current value of conf-dir, configuration files go there,
  - :ui - UI,
  - :mem-stats - most recent mem statst."
  [ui conf-dir connections plugins]
  {:pre [(string? conf-dir)
         (map? connections)
         (sequential? plugins)
         (every? map? plugins)
         ;; Each plugin is a map and has a name.
         (every? :name plugins)
         (every? string? (keys connections))]}
  (let [s {:timer       nil
           :plugins     plugins
           :worksheets  {}
           :connections connections
           :conf-dir    conf-dir
           :conn-list   nil
           :ui          ui
           :mem-stats   nil}]
    (atom s)))

(defn save-conn!
  "Save connection.
  Parameters:
  - sqls-atom - sqls app state, must contain :conn-list,
  - old-conn-data - old connection data if editing, nil if saving new connection,
  - conn-data - new connection data to be stored in settings and displayed in frame."
  [sqls-atom old-conn-data conn-data]
  (let [conn-list-win (:conn-list @sqls-atom)
        conf-dir (:conf-dir @sqls-atom)
        new-conns (stor/add-connection! conf-dir conn-data)
        _ (assert (sequential? new-conns))
        _ (assert (every? (fn [c] (string? (:name c))) new-conns))
        new-conns-is-enabled (for [conn new-conns]
                              [(:name conn)
                               (nil? (-> @sqls-atom :worksheets (get (:name conn))))])]
    (swap! sqls-atom assoc :connections (into {} (map (fn [conn] [(:name conn) conn]) new-conns)))
    ; (set-conns! conn-list-win new-conns)
    (add-conn! conn-list-win conn-data)
    (doseq [[c e] new-conns-is-enabled]
      (if e
        (enable-conn! conn-list-win c)
        (disable-conn! conn-list-win c)))))

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

(defn get-plugins-drivers
  "Ask plugins for known drivers."
  [plugins]
  {:pre [(or
           (nil? plugins)
           (and
             (sequential? plugins)
             (not (empty? plugins))))]
   :post [(or (nil? %)
              (and (seq? %)
                   (every? sequential? %)
                   (every? #(= (count %) 2) %)))]}
  (let [plugins-drivers (map (fn [p] (if-let [clfn (:classes p)]
                                       (clfn)
                                       []))
                             plugins)]
    (let [driver-pairs (apply concat plugins-drivers)]
      driver-pairs)))

(defn get-plugins-conns-drivers
  [plugins conns]
  {:pre [(sequential? plugins)
         (map? conns)]
   :post [(sequential? %)]}
  (let [plugin-drivers (get-plugins-drivers plugins)
        class-names (into {} plugin-drivers) ; e.g. foo.bar.BarDriver -> "Bar"
        plugin-classes (map first plugin-drivers) ; all classes from plugins
        conn-classes (map :class (vals conns))    ; all classes from existing conns
        _ (assert (every? string? conn-classes)
                  (format "Invalid conn classes: %s" (with-out-str (fipp.edn/pprint conn-classes))))
        ;; unique list of classes from both plugins and drivers
        all-classes (sort (into #{} (concat plugin-classes conn-classes)))]
    (assert (every? string? all-classes))
    (map (fn [c] [c (get class-names c)]) all-classes)))

(defn window-closed!
  "Called when one of the windows is closed (either Worksheet or ConnList)."
  [exit-on-close? sqls-atom]
  {:pre [(-> sqls-atom deref :worksheets map?)]}
  (let [s @sqls-atom
        ui (:ui s)]
    (when (and (empty? (:worksheets s))
               (not (:conn-list s)))
      (println (format "last window closed"))
      (when-let [t (:timer s)]
        (destroy-timer! ui t))
      (when exit-on-close?
        (println "bye")
        (System/exit 0)))))

(defn on-conn-list-closed!
  [exit-on-close? sqls-atom]
  (swap! sqls-atom assoc :conn-list false)
  (window-closed! exit-on-close? sqls-atom))

(defn create-worksheet!
  [sqls-atom ui exit-on-close? conn-list-win conn-name]
  {:pre [(not (nil? sqls-atom))
         (not (nil? @sqls-atom))
         (not (nil? conn-list-win))
         (not (nil? conn-name))
         (string? conn-name)]}
  (let [^Conn conn-data (-> @sqls-atom :connections (get conn-name))
        conf-dir (:conf-dir @sqls-atom)]
    (assert (not (nil? conn-data)))
    (assert (not (nil? conf-dir)))
    (if (contains? (:worksheets @sqls-atom) conn-name)
      (warnf "worksheet already present in sqls state")
      (let [plugins (sqls.plugin/get-plugins-by-class (:class conn-data) (:plugins @sqls-atom))
            worksheet-data (stor/load-worksheet-data! conf-dir conn-name)
            worksheet-handlers {:save-worksheet-data (fn [data] (stor/save-worksheet-data! conf-dir conn-name data))
                                :describe-object (fn [conn object-name] (sqls.plugin/describe-object! conn object-name plugins))
                                :list-schemas (fn [conn] (sqls.plugin/list-schemas! conn plugins))
                                :worksheet-closed (fn [conn-name]
                                                    (swap! sqls-atom update :worksheets dissoc conn-name)
                                                    (enable-conn! conn-list-win conn-name)
                                                    (window-closed! exit-on-close? sqls-atom))}
            worksheet (sqls.worksheet/create-and-show-worksheet! ui conn-data worksheet-data worksheet-handlers)]
        (swap! sqls-atom assoc-in [:worksheets conn-name] worksheet)
        (disable-conn! conn-list-win conn-name)))))

(defn get-mem-info!
  []
  (let [runtime (Runtime/getRuntime)
        total (.totalMemory runtime)
        free (.freeMemory runtime)
        used (- total free)
        rss (get-rss!)]
    (format "mem usage: used: %s, allocated: %s, rss: %s"
            (human-readable-size used)
            (human-readable-size total)
            (human-readable-size rss))))

(defn create-sqls-timer!
  [ui sqls-atom]
  (let [timer-fn (fn timer-fn
                   []
                   (let [worksheets (:worksheets @sqls-atom)]
                     (when (not (empty? worksheets))
                       (let [mem-info (get-mem-info!)]
                         (doseq [[_ worksheet] (:worksheets @sqls-atom)]
                           (sqls.worksheet/status-right-text! worksheet mem-info))))))
        ui-timer (create-timer! ui (* 2 4096) timer-fn)]
    ui-timer))

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
   (let [about-text (io/resource "about.txt")
         ^UI ui (create-ui! about-text)]
     (let [plugins builtin-plugins
           cwd (System/getProperty "user.dir")
           conf-dir (if cli-conf-dir cli-conf-dir (conf/find-conf-dir cwd))
           _ (assert conf-dir)
           connections (->> (stor/load-connections! conf-dir)
                            (map (fn [c] [(:name c) c]))
                            (apply concat)
                            (apply hash-map))
           ;; sqls-atom holds high level mutable state of sqls
           ;; state is bad, but inevitable, let's try to have it contained…
           sqls-atom (create-sqls-atom! ui conf-dir connections plugins)
           _ (conf/ensure-conf-dir! conf-dir)
           settings (stor/load-settings! conf-dir)
           drivers (get-plugins-conns-drivers plugins connections)
           handlers {:create-worksheet (partial create-worksheet! sqls-atom ui exit-on-close?)
                     :save-conn (partial save-conn! sqls-atom)
                     :test-conn test-conn!
                     :delete-connection! (partial stor/delete-connection! conf-dir)
                     :conn-list-closed (partial on-conn-list-closed! exit-on-close? sqls-atom)}
           ^ConnListWindow conn-list-window (create-conn-list-window! ui
                                                                      drivers
                                                                      ;; TODO: UI should not know about plugins
                                                                      builtin-plugins
                                                                      handlers
                                                                      (->> @sqls-atom
                                                                           :connections
                                                                           vals
                                                                           (sort-by :name)))]
       (swap! sqls-atom assoc :conn-list conn-list-window)
       (let [timer (create-sqls-timer! ui sqls-atom)]
         (println (format "timer: %s" timer))
         (swap! sqls-atom assoc :timer timer))
       (show-conn-list-window! conn-list-window)))))

(defn -main
  [& args]
  (let [{:keys [options errors summary]} (cli/parse-opts args cli-options)]
    (cond
      (:help options) (exit 0 (usage summary))
      errors (exit 1 (error-msg errors)))
    (-sqls true)))

