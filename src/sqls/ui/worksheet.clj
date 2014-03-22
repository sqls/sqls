
(ns sqls.ui.worksheet
  (:use [clojure.string :only (join split-lines trim)])
  (:require seesaw.core)
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
                                                  :preferred-size [800 :by 400])
        log-text (seesaw.core/text :id :log :multi-line? true :editable? false)
        log-panel (seesaw.core/vertical-panel :id :log-panel
                                              :items [log-text])
        tabs-panel (seesaw.core/tabbed-panel :id :tabs
                                       :tabs [{:title "Results" :content results-panel}
                                              {:title "Log" :content log-panel}])
        menu-panel (seesaw.core/horizontal-panel :id :menu-panel
                                                 :items [
                                                         (seesaw.core/button :id :new
                                                                             :icon (seesaw.icon/icon "new.png"))
                                                         (seesaw.core/button :id :save
                                                                             :icon (seesaw.icon/icon "floppy.png"))
                                                         (seesaw.core/button :id :open
                                                                             :icon (seesaw.icon/icon "open.png"))
                                                         (seesaw.core/button :id :explain
                                                                             :text "Explain plan")
                                                         (seesaw.core/button :id :execute
                                                                             :text "Execute")
                                                         (seesaw.core/button :id :commit
                                                                             :text "Commit")
                                                         (seesaw.core/button :id :rollback
                                                                             :text "Rollback")
                                                         ])
        center-panel (seesaw.core/vertical-panel :items [query-text-area tabs-panel])
        south-panel (seesaw.core/horizontal-panel :items [""])
        border-panel (seesaw.core/border-panel :north menu-panel
                                               :center center-panel
                                               :south south-panel)
        worksheet-frame (seesaw.core/frame
                          :title "SQL Worksheet"
                          :content border-panel)]
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
    block-text))


(defn show-results!
  "Display results inside results panel.
  This involves building results-table UI with accompanying controls.
  Parameters:

  - frame - worksheet frame,
  - columns - column names,
  - rows - a pair of semi strict and lazy sequences of rows do display.

  Scroll view is being configured so that if user scrolls close to the end of the table, new
  rows are fetched from second element of rows pair.
  "
  [frame columns rows]
  (let [
        [strict-rows lazy-rows] rows
        ; _ (println "strict-rows:" strict-rows)
        ; _ (println "lazy-rows:" lazy-rows)
        results-table (seesaw.core/table :auto-resize :off
                                         :model [:columns columns
                                                 :rows strict-rows])
        results-table-scrollable (seesaw.core/scrollable results-table)
        results-panel (seesaw.core/select frame [:#results-panel])
        to-remove (seesaw.core/select frame [:#results-panel :> :*])]
    ; (println "strict rows count:" (count strict-rows))
    ; (println "lazy rows count:" (count lazy-rows))  ; this obviously shouldn't happen
    (doall (map (partial seesaw.core/remove! results-panel) to-remove))
    (seesaw.core/add! results-panel results-table-scrollable)
    (let [table-column-model (.getColumnModel results-table)
          column-count (count columns)
          row-count (count strict-rows)
          max-column-widths (vec (for [column-index (range column-count)]
                                   (max 4
                                        (let [
                                              column-values (map str (map #(get % column-index) strict-rows))
                                              column-value-lengths (map count column-values)
                                              max-column-value-length (apply max column-value-lengths)]
                                          max-column-value-length))))
          ]
      (doall (for [column-index (range column-count)]
               (let [table-column (.getColumn table-column-model column-index)
                     column-width (get max-column-widths column-index)]
                 (.setPreferredWidth table-column (* column-width 10))
                 ))))))
