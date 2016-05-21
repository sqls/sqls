(ns sqls.worksheet
  (:require
    [io.aviso.exception :refer [format-exception]]
    [clojure.string :refer [blank? lower-case trim]]
    [sqls.model :refer [conn?]]
    sqls.ui.proto
    [sqls.util :as util]
    [sqls.util :refer [atom? not-nil?]]
    sqls.jdbc)
  (:import [clojure.lang Agent Atom]
           [java.sql Connection SQLException]
           [sqls.ui.proto UI WorksheetWindow]))

(defn worksheet-agent-error-handler
  "Error handler for worksheet agents."
  [^Agent a
   ^Throwable e]
  (println (format-exception e)))

(defn on-worksheet-window-closed!
  "Called from Worksheet UI when windows was closed.
  Cleanup of worksheet happens here:

  - try to close connection,
  - remove agent and atom."
  [worksheet]
  (assert (atom? worksheet))
  (println "window closed")
  (let [conn-data (:conn-data @worksheet)
        conn-name (:name conn-data)
        ^Connection conn (:conn @worksheet)
        agent (:agent @worksheet)
        window (:window @worksheet)]
    (sqls.ui.proto/release-resources! window)
    (send-off agent (fn [_]
                      (.close conn)
                      nil))
    (assert (not-nil? conn-name))
    (assert (instance? String conn-name))
    (reset! worksheet {})))

(defn commit!
  [worksheet]
  (assert (not= worksheet nil))
  (let [^Connection conn (@worksheet :conn)]
    (assert (not= conn nil))
    (.commit conn)))

(defn rollback!
  [worksheet]
  (assert (not= @worksheet nil))
  (let [^Connection conn (@worksheet :conn)]
    (assert (not= conn nil))
    (.rollback conn)))

(defn describe!
  [worksheet]
  (let [conn (@worksheet :conn)
        describe-object (-> @worksheet :handlers :describe-object)]
    (when (and conn describe-object)
      (let [window (:window @worksheet)
            _ (assert (not (nil? window)))
            object-name (sqls.ui.proto/get-word! window)
            maybe-desc (describe-object conn object-name)]
        (println (format "description: %s" maybe-desc))
        (when maybe-desc
          (sqls.ui.proto/select-tab! window 1)
          (sqls.ui.proto/log! window (str maybe-desc "\n")))))))

(defn clear-results!
  [worksheet]
  {:pre [(:window @worksheet)]}
  (swap! worksheet dissoc :result)
  (sqls.ui.proto/clear-results! (:window @worksheet)))

(defn worksheet-cmds!
  "Get worksheet commands.
  Can have side effects.
  Params:
  - text - text entered in search box, can be nil and can be empty."
  [worksheet text]
  (let [;; Check if given cmd matches given search text.
        ;; TODO: this should be handled by commands: some commands might accept everything for example.
        match (fn [cmd ^String text]
                (let [^String cmd-text (:text cmd)]
                  (assert cmd-text)
                  (or (blank? text) ; empty text matches all
                      (.contains (lower-case cmd-text) (lower-case text)))))
        ;; TODO: Core should be owner of commands, plugins should be able to provide commands.
        all-commands [{:text "Commit"
                       :fn (partial commit! worksheet)}
                      {:text "Rollback"
                       :fn (partial rollback! worksheet)}
                      {:text "Describe"
                       :fn (partial describe! worksheet)}
                      {:text "Clear results"
                       :fn (partial clear-results! worksheet)}]]
    (filter (fn [cmd] (match cmd text)) all-commands)))

(defn create-worksheet!
  "Create worksheet atom, including worksheet frame.

  Params:

  - ui - UI system to use to create actual window,
  - conn-data - contains connection spec,
  - worksheet-data - worksheet workspace state restored from storage,
  - handlers - various high-level handlers for worksheet.

  Worksheet atom is a structure map which contains following keys:

  - :frame - the ui frame,
  - :conn-data - specification of connection parameters as map,
  - :conn - the connection itself, created after connecting,
  - :result - result struct map,
  - :agent - agent used to run queries and other IO jobs that are meant to run one-by-one,
  - :state - state of the worksheet, atomically changed from UI, allowed states:

    - :idle,
    - :busy - a query is running, worksheet is waiting for results.

  Result is a struct map with following keys:

  - columns - columns,
  - rows - a pair of semi-strict and lazy sequences of rows.
  "
  [ui conn-data worksheet-data handlers]
  {:pre [(not (nil? ui))
         (instance? UI ui)
         (not (nil? conn-data))
         (conn? conn-data)
         (or (nil? worksheet-data)
             (map? worksheet-data))
         (:save-worksheet-data handlers)]
   :post [(not (nil? %))
          (atom? %)
          (not (nil? (-> % deref :window)))]}
  (let [conn-name (:name conn-data)
        ;; TOOD: a way for create-worksheet-window! to fail gracefully
        ^WorksheetWindow worksheet-window (sqls.ui.proto/create-worksheet-window! ui conn-name worksheet-data)
        _ (assert (not (nil? worksheet-window)))
        _ (assert (instance? WorksheetWindow worksheet-window))
        ;; UI commands are send from UI and are executed one-by one in this agent
        worksheet-cmd-agent (agent {} :error-handler worksheet-agent-error-handler)
        ;; long running jobs are executed in this agent
        worksheet-work-agent (agent nil :error-handler worksheet-agent-error-handler)
        worksheet (atom {:window worksheet-window
                         :conn-data conn-data
                         :agent worksheet-cmd-agent
                         :work-agent worksheet-work-agent
                         :state :idle
                         :handlers handlers})]
    worksheet))

(defn connect-worksheet!
  "Create JDBC connection for this worksheet.
  Returns worksheet with conn field set."
  [ui
   ^Atom worksheet]
  {:pre [(not (-> @worksheet :conn-data nil?))]}
  (let [conn-data (:conn-data @worksheet)
        conn-or-error (sqls.jdbc/connect! conn-data)
        ^Connection conn (conn-or-error :conn)
        ^String msg (conn-or-error :msg)
        ^String desc (conn-or-error :desc)
        ]
    (if (not= conn nil)
      (do
        (swap! worksheet assoc :conn conn)
        worksheet)
      (sqls.ui.proto/show-error! ui msg))))

(defn show-results!
  "Display results from cursor in worksheet frame."
  [worksheet cursor]
  {:pre [(:window @worksheet)]}
  (let [columns (first cursor)
        rows-lazy (rest cursor)
        rows-semi-strict (take 256 rows-lazy)
        rows [rows-semi-strict rows-lazy]
        result {:columns columns :rows rows}
        worksheet-win (:window @worksheet)]
    (swap! worksheet assoc :result result)
    (sqls.ui.proto/show-results! worksheet-win columns rows)))

(defn fix-sql
  "Cleanup sql before running it. Expects trimmed sql."
  [sql]
  (if (util/endswith sql ";")
    (subs sql 0 (- (count sql) 1))
    sql))

(defn execute!
  "Start executing query or sql command in worksheet - executed in \"worker\" worksheet agent."
  [worksheet-agent-state
   ^Atom worksheet
   ^String sql]
  {:pre [(:window @worksheet)
         (:conn @worksheet)]}
  (let [win (:window @worksheet)
        ^Connection conn (@worksheet :conn)]
    (sqls.ui.proto/log! win (format "Executing:\n%s\n" sql))
    (sqls.ui.proto/status-text! win "Executing...")
    (try
      (let [cursor (sqls.jdbc/execute! conn sql)]
        (when-not (nil? cursor)
          (show-results! worksheet cursor)
          (sqls.ui.proto/select-tab! win 0))
        (sqls.ui.proto/log! win "Done\n")
        (sqls.ui.proto/status-text! win "Done")
        (swap! worksheet assoc :state :idle))
      (catch SQLException e
        (do
          (sqls.ui.proto/log! win (format "Failed to execute SQL: %s\n" (str e)))
          (sqls.ui.proto/select-tab! win 1)
          (sqls.ui.proto/status-text! win "Error")
          (swap! worksheet assoc :state :idle)))))
  worksheet-agent-state)

(defn on-ctrl-enter!
  "Executed by frame on Ctrl-Enter press.

  SQL statement is executed only if worksheet status is idle.
  Worksheet status is changed to busy (using atom based transaction - so there's no race possible).
  If :idle -> :busy change is successful, then new job is submitted to agent.

  Saves worksheet data before trying to execute.
  "
  [^Atom worksheet]
  {:pre [worksheet
         (:window @worksheet)
         (:save-worksheet-data (:handlers @worksheet))]}
  (let [worksheet-agent (:agent @worksheet)]
    (send worksheet-agent
          (fn [agent-state worksheet]
            (if (= (:state @worksheet) :idle)
              (do
                (let [handlers (:handlers @worksheet)
                  frame (:window @worksheet)
                  save-worksheet-data (:save-worksheet-data handlers)
                  contents (sqls.ui.proto/get-contents! frame)
                  ;; dimensions is info about window position and size
                  ;; dimensions is map of:
                  ;; - :position - vector of two numbers,
                  ;; - :size - vector of two numbers,
                  ;; - :maximized - boolean.
                  dimensions (sqls.ui.proto/get-dimensions! frame)
                  _ (print (format "dimensions:\n%s" (with-out-str (fipp.edn/pprint dimensions))))
                  _ (assert (or (:maximized dimensions)
                                (and (:size dimensions)
                                     (:position dimensions)
                                     (= 2 (-> dimensions :position count))
                                     (= 2 (-> dimensions :size count)))))
                  split-ratio (sqls.ui.proto/get-split-ratio! frame)]
                  (save-worksheet-data {:contents contents
                                        :dimensions dimensions
                                        :split-ratio split-ratio}))
                (swap! worksheet assoc :state :busy)
                (let [sql (-> (@worksheet :window)
                              (sqls.ui.proto/get-sql!)
                              (trim)
                              (fix-sql))]
                (send-off worksheet-agent execute! worksheet sql)))
              (println "worksheet is not idle")))
          worksheet)))

(defn save!
  [worksheet]
  (let [frame (@worksheet :frame)
        path (sqls.ui.proto/choose-save-file! frame)
        contents (sqls.ui.proto/get-contents! frame)]
    ;; TODO: use core to spit
    (spit path contents)))

;; TODO
(defn open!
  [worksheet]
  (println "open!" worksheet))

(defn status-right-text!
  [worksheet text]
  (let [window (:window @worksheet)]
    (sqls.ui.proto/status-right-text! window text)))


(defn create-and-show-worksheet!
  "Create and show worksheet, intiate connecting, return worksheet data structure.

  Params:

  - conn-data - connection parameters including name and jdbc connection string,
  - worksheet-data - worksheet data, like contents, scroll position, possibly other (I start with contents and will
    add more stuff later),
  - handlers - a map of functions that talk to other submodules, since I don't want to use other submodules directly:

    - :save-worksheet-data - takes one parameter, a map of worksheet data, of which currently one parameter is
      supported:

      - :contents - worksheet contents

    - :worksheet-closed conn-name - removes this worksheet from app, to be executed after disposal
      of worksheet frame.
  "
  [ui
   conn-data
   worksheet-data
   handlers]
  (assert (instance? UI ui))
  (assert (or (nil? worksheet-data) (map? worksheet-data)))
  (let [worksheet (create-worksheet! ui conn-data worksheet-data handlers)]
    (assert (not (nil? (:window @worksheet))))
    (let [window (:window @worksheet)
          handlers {:ctrl-enter (partial on-ctrl-enter! worksheet)
                    ;; TODO: rename :worksheet-commands :/
                    :worksheet-commands (partial worksheet-cmds! worksheet)
                    :commit (partial commit! worksheet)
                    :rollback (partial rollback! worksheet)
                    :execute (partial on-ctrl-enter! worksheet)
                    :save (partial save! worksheet)
                    :open (partial open! worksheet)
                    :closed (fn []
                              ;; Windows are never re-opened, so if this UI frame was closed, then
                              ;; we can also drop resources associated with this worksheet structure.
                              (on-worksheet-window-closed! worksheet)
                              ;; Now that both window and this worksheet structure is disposed,
                              ;; we can call parent so it can remove us from its structure.
                              ((:worksheet-closed handlers) (:name conn-data)))}]
      (sqls.ui.proto/set-worksheet-handlers! window handlers) ; set handlers should happen in "create-worksheet!"
      (sqls.ui.proto/show-worksheet-window! window)
      (connect-worksheet! ui worksheet)
      worksheet)))


