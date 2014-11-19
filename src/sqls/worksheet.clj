
(ns sqls.worksheet
  (:require
    [clojure.string :refer [trim]]
    [sqls.ui :as ui]
    [sqls.ui.worksheet :as ui-worksheet]
    [sqls.util :as util]
    [sqls.util :refer [atom? not-nil?]]
    sqls.jdbc)
  (:import [clojure.lang Agent Atom Keyword]
           [javax.swing JFrame]
           [java.sql Connection SQLException]))


(defn worksheet-agent-error-handler
  "Error handler for worksheet agents."
  [^Agent a
   ^Throwable e]
  (println (format "agent %s got exception %s" a e)))


(defn on-worksheet-window-closed
  "Called from Worksheet UI when windows was closed.
  Cleanup of worksheet happens here:

  - try to close connection,
  - remove agent and atom,
  - call parent (sqls) to remove ourselves from central structures - this
    happens by calling remove-worksheet-from-sqls with conn-name as only parameter."
  [worksheet remove-worksheet-from-sqls]
  (assert (atom? worksheet))
  (assert (not-nil? remove-worksheet-from-sqls))
  (assert (ifn? remove-worksheet-from-sqls))
  (println "window closed")
  (let [conn-data (:conn-data @worksheet)
        conn-name (conn-data "name")
        ^Connection conn (:conn @worksheet)
        agent (:agent @worksheet)]
    (send-off agent (fn [_]
                      (.close conn)
                      nil))
    (assert (not-nil? conn-name))
    (assert (instance? String conn-name))
    (reset! worksheet {})
    (remove-worksheet-from-sqls conn-name)))


(defn create-worksheet
  "Create worksheet atom, including worksheet frame.

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
  [conn-data worksheet-data handlers]
  (assert (or (nil? worksheet-data) (map? worksheet-data)))
  (let [conn-name (conn-data "name")
        remove-worksheet-from-sqls (:remove-worksheet-from-sqls handlers)
        _ (assert (not= remove-worksheet-from-sqls nil))
        worksheet-frame (ui-worksheet/create-worksheet-frame worksheet-data conn-name)
        worksheet-agent (agent {}
                               :error-handler worksheet-agent-error-handler)
        worksheet (atom {:frame worksheet-frame
                         :conn-data conn-data
                         :agent worksheet-agent
                         :state :idle
                         :handlers handlers})]
    ; this needs to happen in separate function, because we have circular dependency:
    ; we want to have worksheet data structure with frame and other stuff, but we
    ; also want event handlers originating from frame to modify worksheet structures
    (ui-worksheet/set-on-windows-closed-handler
      worksheet-frame
      ; partial holds relevant references, and is called by ui without parameters
      (partial on-worksheet-window-closed worksheet remove-worksheet-from-sqls))
    worksheet))


(defn connect-worksheet!
  "Create JDBC connection for this worksheet.
  Returns worksheet with conn field set."
  [^Atom worksheet]
  (assert (not= worksheet nil))
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
      (ui/show-error! msg desc))))


(defn show-results!
  "Display results from cursor in worksheet frame."
  [worksheet cursor]
  (assert (not= (:frame @worksheet) nil))
  (let [columns (first cursor)
        rows-lazy (rest cursor)
        rows-semi-strict (take 256 rows-lazy)
        rows [rows-semi-strict rows-lazy]
        result {:columns columns :rows rows}]
    (swap! worksheet assoc :result result)
    (ui-worksheet/show-results! worksheet columns rows)))


(defn fix-sql
  "Cleanup sql before running it. Expects trimmed sql."
  [sql]
  (if (util/endswith sql ";")
    (subs sql 0 (- (count sql) 1))
    sql))


(defn swap-worksheet-state
  [from-state to-state worksheet-atom-value]
  (let [^Keyword current-state (worksheet-atom-value :state)]
    (if (= current-state from-state)
      (do
        (println (format "ok, setting state to %s" to-state))
        (assoc worksheet-atom-value :state to-state))
      (throw (Exception. (format "can't change worksheet state from %s to %s, because current state is %s" from-state to-state current-state))))))


(defn execute!
  "Job executed in worksheet agent.
  Worksheet state must be busy when entering this function.
  Worksheet state must stay busy when this function is executing.
  Last thing this function does is to set state back to idle.

  For now worksheet agent state stays unchanged.
  "
  [worksheet-agent-state
   ^Atom worksheet
   ^String sql]
  (let [^JFrame frame (:frame @worksheet)
        ^Connection conn (@worksheet :conn)]
    (assert (not= frame nil))
    (assert (not= conn nil))
    (ui-worksheet/log frame (format "Executing \"%s\"...\n" sql))
    (ui-worksheet/status-text frame "Executing...")
    (ui-worksheet/clear-results! frame)
    (try
      (let [cursor (sqls.jdbc/execute! conn sql)]
        (if (not= cursor nil)
          (show-results! worksheet cursor))
        (ui-worksheet/log frame "Done\n")
        (ui-worksheet/status-text frame "Done"))
      (catch SQLException e
        (do
          (ui-worksheet/log frame (format "Failed to execute SQL: %s\n" (str e)))
          (ui-worksheet/status-text frame "Error"))))
    (swap! worksheet (partial swap-worksheet-state :busy :idle)))
  (println (format "execute! returning new agent state %s" worksheet-agent-state))
  worksheet-agent-state)


; (defn explain!
;   [worksheet]
;   (println "explain!"))


(defn on-ctrl-enter
  "Executed by frame on Ctrl-Enter press.

  SQL statement is executed only if worksheet status is idle.
  Worksheet status is changed to busy (using atom based transaction - so there's no race possible).
  If :idle -> :busy change is successful, then new job is submitted to agent.

  Saves worksheet data before trying to execute.
  "
  [^Atom worksheet]
  (assert (not= worksheet nil))
  (let [handlers (:handlers @worksheet)
        frame (:frame @worksheet)
        save-worksheet-data (:save-worksheet-data handlers)
        contents (ui-worksheet/get-contents frame)
        dimensions (ui-worksheet/get-worksheet-frame-dimensions frame)
        split-ratio (ui-worksheet/get-split-ratio frame)]
    (save-worksheet-data {"contents" contents
                          "dimensions" dimensions
                          "split-ratio" split-ratio}))
  (let [^Agent worksheet-agent (@worksheet :agent)
        swap-idle-to-busy (partial swap-worksheet-state :idle :busy)]
    (try
      (do
        (swap! worksheet swap-idle-to-busy)
        (println "state changed")
        (let [^String sql (-> (@worksheet :frame)
                              (ui-worksheet/get-sql)
                              (trim)
                              (fix-sql))]
          ; now we can proceed - send the job to agent
          (send-off worksheet-agent execute! worksheet sql)))
      (catch Exception e
        (println "couldn't change state:" e)))))


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


(defn new!
  [worksheet]
  (println "new!" worksheet))


(defn save!
  [worksheet]
  (let [frame (@worksheet :frame)
        path (ui-worksheet/choose-save-file frame)
        contents (ui-worksheet/get-contents frame)]
    (spit path contents)))


(defn open!
  [worksheet]
  (println "open!" worksheet))


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

    - :remove-worksheet-from-sqls conn-name - removes this worksheet from app, to be executed after disposal
      of worksheet frame.


  "
  [conn-data
   worksheet-data
   handlers]
  (assert (map? conn-data))
  (assert (or (nil? worksheet-data) (map? worksheet-data)))
  (let [worksheet (create-worksheet conn-data worksheet-data handlers)
        frame (:frame @worksheet)
        connected-worksheet (connect-worksheet! worksheet)]
    (assert (not= worksheet nil))
    (assert (not= frame nil))
    (if (not= connected-worksheet nil)
      (do
        (ui-worksheet/set-ctrl-enter-handler frame (partial on-ctrl-enter connected-worksheet))
        ; (ui-worksheet/set-on-explain-handler frame (partial explain! connected-worksheet))
        (ui-worksheet/set-on-commit-handler frame (partial commit! connected-worksheet))
        (ui-worksheet/set-on-rollback-handler frame (partial rollback! connected-worksheet))
        (ui-worksheet/set-on-execute-handler frame (partial on-ctrl-enter connected-worksheet))
        (ui-worksheet/set-on-new-handler frame (partial new! connected-worksheet))
        (ui-worksheet/set-on-save-handler frame (partial save! connected-worksheet))
        (ui-worksheet/set-on-open-handler frame (partial open! connected-worksheet))
        (ui-worksheet/show! frame)
        worksheet))))


