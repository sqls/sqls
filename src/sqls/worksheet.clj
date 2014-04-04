
(ns sqls.worksheet
  (:use [clojure.string :only [trim]])
  (:require
    [sqls.ui :as ui]
    [sqls.ui.worksheet :as ui-worksheet]
    [sqls.util :as util]
    sqls.jdbc
    ))


(defn worksheet-agent-error-handler
  "Error handler for worksheet agents."
  [^clojure.lang.Agent a
   ^Throwable e]
  (println (format "agent %s got exception %s" a e)))


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
  [conn-data]
  (let [worksheet-frame (ui-worksheet/create-worksheet-frame)
        worksheet-agent (agent {}
                               :error-handler worksheet-agent-error-handler)
        worksheet (atom {:frame worksheet-frame
                         :conn-data conn-data
                         :agent worksheet-agent
                         :state :idle})]
    worksheet))


(defn connect-worksheet!
  "Create JDBC connection for this worksheet.
  Returns worksheet with conn field set."
  [^clojure.lang.Atom worksheet]
  (assert (not= worksheet nil))
  (let [conn-data (:conn-data @worksheet)
        conn-or-error (sqls.jdbc/connect! conn-data)
        ^java.sql.Connection conn (conn-or-error :conn)
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
  (let [^clojure.lang.Keyword current-state (worksheet-atom-value :state)]
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
   ^clojure.lang.Atom worksheet
   ^String sql]
  (let [^javax.swing.JFrame frame (:frame @worksheet)
        ^java.sql.Connection conn (@worksheet :conn)]
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
      (catch java.sql.SQLException e
        (do
          (ui-worksheet/log frame (format "Failed to execute SQL: %s\n" (str e)))
          (ui-worksheet/status-text frame "Error"))))
    (swap! worksheet (partial swap-worksheet-state :busy :idle)))
  worksheet-agent-state)


; (defn explain!
;   [worksheet]
;   (println "explain!"))


(defn on-ctrl-enter
  "Executed by frame on Ctrl-Enter press.

  SQL statement is executed only if worksheet status is idle.
  Worksheet status is changed to busy (using atom based transaction - so there's no race possible).
  If :idle -> :busy change is successful, then new job is submitted to agent.
  "
  [^clojure.lang.Atom worksheet]
  (assert (not= worksheet nil))
  (let [^clojure.lang.Agent worksheet-agent (@worksheet :agent)
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
  (let [^java.sql.Connection conn (@worksheet :conn)]
    (assert (not= conn nil))
    (.commit conn)))


(defn rollback!
  [worksheet]
  (assert (not= @worksheet nil))
  (let [^java.sql.Connection conn (@worksheet :conn)]
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
  "Create and show worksheet, intiate connecting, return worksheet data structure."
  [conn-data]
  (let [worksheet (create-worksheet conn-data)
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
        nil))))


