
(ns sqls.worksheet
  (:use [clojure.string :only [trim]])
  (:require
    [sqls.ui :as ui]
    [sqls.ui.worksheet :as ui-worksheet]
    [sqls.util :as util]
    sqls.jdbc
    ))


(defn create-worksheet
  "Create worksheet data, including worksheet frame.

  Worksheet data structure contains following keys:

  - frame - the ui frame,
  - conn-data - specification of connection parameters as map,
  - conn - the connection itself, created after connecting.
  "
  [conn-data]
  (let [worksheet-frame (ui-worksheet/create-worksheet-frame)
        worksheet {:frame worksheet-frame
                   :conn-data conn-data}]
    worksheet))


(defn connect-worksheet!
  "Create JDBC connection for this worksheet.
  Returns worksheet with conn field set."
  [worksheet]
  (let [conn-data (:conn-data worksheet)
        conn (sqls.jdbc/connect! conn-data)]
    (assoc worksheet :conn conn)))


(defn show-results!
  "Display results from cursor in worksheet frame."
  [worksheet cursor]
  (let [columns (first cursor)
        rows-lazy (rest cursor)
        rows-semi-strict (take 1024 rows-lazy)
        rows [rows-semi-strict rows-lazy]
        frame (worksheet :frame)]
    (ui-worksheet/show-results! frame columns rows)))


(defn fix-sql
  "Cleanup sql before running it. Expects trimmed sql."
  [sql]
  (if (util/endswith sql ";")
    (subs sql 0 (- (count sql) 1))
    sql))


(defn execute!
  "Execute current SQL command from given worksheet.
  Is SQL returns rows, then fetch some rows and display them.
  "
  [worksheet]
  (let [sql (fix-sql (trim (ui-worksheet/get-sql (worksheet :frame))))
        conn (worksheet :conn)]
    (let [cursor (sqls.jdbc/execute! conn sql)]
      (if (not= cursor nil)
        (show-results! worksheet cursor)))))


(defn on-ctrl-enter
  "Executed by frame on Ctrl-Enter press."
  [worksheet]
  (execute! worksheet))


(defn create-and-show-worksheet!
  "Create and show worksheet, intiate connecting, return worksheet data structure."
  [conn-data]
  (let [worksheet (create-worksheet conn-data)
        connected-worksheet (connect-worksheet! worksheet)
        frame (:frame worksheet)]
    (ui-worksheet/set-ctrl-enter-handler frame (partial on-ctrl-enter connected-worksheet))
    (ui-worksheet/show! (:frame worksheet))
    worksheet))

