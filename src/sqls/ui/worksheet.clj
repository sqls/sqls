
(ns sqls.ui.worksheet
  (:use [clojure.string :only (join split-lines trim)])
  (:require seesaw.core)
  (:require seesaw.rsyntax)
  (:require seesaw.keystroke)
  (:import javax.swing.KeyStroke))


(defn on-query-text-area-key-press
  "Handle key press in query text area of SQL Worksheet."
  [e]
)


(defn create-worksheet-frame
  "Create worksheet frame.
  Frame contains following widgets:
  
  - :sql - text are with SQL statements,
  - :results-panel - container that contains results, which in turn contains table with :id :results,
    contents of this panel are meant to be replaced on each query execution.
  "
  []
  (let [query-text-area (seesaw.rsyntax/text-area :id :sql
                                                  :syntax :sql
                                                  :columns 80
                                                  :rows 25
                                                  :listen [:key-pressed on-query-text-area-key-press])
        results-panel (seesaw.core/vertical-panel :id :results-panel
                                                  :preferred-size [0 :by 400])
        worksheet-frame (seesaw.core/frame :title "SQL Worksheet"
                                 :content (seesaw.core/vertical-panel :items [query-text-area results-panel]))]
    (seesaw.core/pack! worksheet-frame)
    worksheet-frame
    )
)


(defn on-key-press
  "Key press handler for sql text area. Calls first parameter (handler), meant to be curried."
  [handler keystroke e]
  (let [event-key-stroke (javax.swing.KeyStroke/getKeyStrokeForEvent e)]
    (if (= keystroke event-key-stroke)
      (handler))))


(defn set-ctrl-enter-handler
  "Set Ctrl-Enter handler on frame. Handler should expect no parameters (all needed parameters
  should be baked in using partial or similar means."
  [frame handler]
  (let [ctrl-enter-keystroke (seesaw.keystroke/keystroke "control ENTER")
        sql-text-area (seesaw.core/select frame [:#sql])]
    (seesaw.core/listen sql-text-area :key-pressed (partial on-key-press handler ctrl-enter-keystroke))))


(defn show!
  "Show worksheet frame."
  [frame]
  (seesaw.core/show! frame))


(defn extend-nonempty-lines-range
  "Return a range (that is, pair of line numbers) that include adjacent non-empty lines.
  
  First see if start can be decreased, and if it can, then return result of calling recursively with decreased start.
  Otherwise see if end can be increased, and again if it can, then return result of calling recursively with increased end.
  Finally if non of above worked, just return [start end].
  "
  [lines start end]
  (let [can-decrease-start (and
                             (> start 0)
                             (not= (trim (lines (dec start))) ""))]
    (if can-decrease-start
      (extend-nonempty-lines-range lines (dec start) end)
      (let [can-increase-end (and
                               (< end (dec (count lines)))
                               (not= (trim (lines (inc end))) ""))]
        (if can-increase-end
          (extend-nonempty-lines-range lines start (inc end))
          [start end])))))


(defn get-line-no
  "Get line number from text and index."
  [text position]
  (let [before-position-text (subs text 0 position)
        _ (println "text before position:" before-position-text)
        is-newline (fn [c] (= (str c) "\n"))]
    (count (filter is-newline before-position-text))))


(defn get-sql
  "Extract current SQL text from frame."
  [frame]
  (let [all-text (seesaw.core/value (seesaw.core/select frame [:#sql]))
        caret-position (seesaw.core/config (seesaw.core/select frame [:#sql]) :caret-position)
        all-text-lines (split-lines all-text)
        line-no (get-line-no all-text caret-position)
        [start end] (extend-nonempty-lines-range all-text-lines line-no line-no)
        block-lines (subvec all-text-lines start (inc end))
        block-text (join "\n" block-lines)]
    (println "caret position:" caret-position)
    (println "start line of block:" start)
    (println "end line of block:" end)
    block-text))
  

(defn show-results!
  "Display results inside results panel.
  This involves building results-table UI with accompanying controls.
  Parameters:
  
  - frame - worksheet frame,
  - columns - column names,
  - rows - a lazy sequence of rows do display.
  
  "
  [frame columns rows]
  (let [results-table (seesaw.core/scrollable (seesaw.core/table :model [:columns columns
                                                             :rows rows]))
        results-panel (seesaw.core/select frame [:#results-panel])
        to-remove (seesaw.core/select frame [:#results-panel :> :*])]
    (println "removing from:" results-panel)
    (println "objects to remove:" to-remove)
    (doall (map (partial seesaw.core/remove! results-panel) to-remove))
    (seesaw.core/add! results-panel results-table)))
  
  
  
  
  
  
  