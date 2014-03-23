
(ns sqls.worksheet
  (:use [clojure.string :only [trim]])
  (:require
    [sqls.ui :as ui]
    [sqls.ui.worksheet :as ui-worksheet]
    [sqls.util :as util]
    sqls.jdbc
    ))


(defn create-worksheet
  "Create worksheet atom, including worksheet frame.

  Worksheet atom is a structure map which contains following keys:

  - frame - the ui frame,
  - conn-data - specification of connection parameters as map,
  - conn - the connection itself, created after connecting,
  - result - result struct map.

  Result is a struct map with following keys:

  - columns - columns,
  - rows - a pair of semi-strict and lazy sequences of rows.
  "
  [conn-data]
  (let [worksheet-frame (ui-worksheet/create-worksheet-frame)
        worksheet (atom {:frame worksheet-frame
                         :conn-data conn-data})]
    worksheet))


(defn connect-worksheet!
  "Create JDBC connection for this worksheet.
  Returns worksheet with conn field set."
  [worksheet]
  (let [conn-data (:conn-data @worksheet)
        conn (sqls.jdbc/connect! conn-data)]
    (swap! worksheet assoc :conn conn)
    worksheet))


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


(defn execute!
  "Execute current SQL command from given worksheet.
  Is SQL returns rows, then fetch some rows and display them.
  "
  [worksheet]
  (let [frame (:frame @worksheet)
        _ (assert (not= frame nil))
        sql (fix-sql (trim (ui-worksheet/get-sql frame)))
        conn (@worksheet :conn)]
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
        frame (:frame @worksheet)]
    (ui-worksheet/set-ctrl-enter-handler frame (partial on-ctrl-enter connected-worksheet))
    (ui-worksheet/show! frame)
    worksheet))
