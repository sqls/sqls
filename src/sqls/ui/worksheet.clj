
(ns sqls.ui.worksheet
  (:use [clojure.string :only (join split-lines trim)])
  (:require seesaw.chooser)
  (:require seesaw.core)
  (:require seesaw.keystroke)
  (:require seesaw.rsyntax)
  (:require seesaw.table)
  (:import javax.swing.JFrame)
  (:import javax.swing.JTable)
  (:import javax.swing.KeyStroke))


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
                                                  :rows 25)
        results-panel (seesaw.core/vertical-panel :id :results-panel
                                                  :preferred-size [800 :by 400])
        log-text (seesaw.core/text :id :log :multi-line? true :editable? false)
        log-panel (seesaw.core/vertical-panel :id :log-panel
                                              :items [(seesaw.core/scrollable log-text
                                                                              :id :log-scrollable)])
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
                                                         ; (seesaw.core/button :id :explain
                                                         ;                     :text "Explain plan")
                                                         (seesaw.core/button :id :execute
                                                                             :text "Execute")
                                                         (seesaw.core/button :id :commit
                                                                             :text "Commit")
                                                         (seesaw.core/button :id :rollback
                                                                             :text "Rollback")
                                                         ])
        center-panel (seesaw.core/vertical-panel :items [query-text-area tabs-panel])
        south-panel (seesaw.core/horizontal-panel :items [(seesaw.core/label :id :status-bar-text
                                                                             :text " ")])
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


(defn dispose-worksheet-frame!
  "Dispose worksheet frame."
  [frame]
  (seesaw.core/dispose! frame))


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
  (assert (not= frame nil))
  (let [ctrl-enter-keystroke (seesaw.keystroke/keystroke "control ENTER")
        sql-text-area (seesaw.core/select frame [:#sql])]
    (seesaw.core/listen sql-text-area :key-pressed (partial on-key-press handler ctrl-enter-keystroke))))


; (defn set-on-explain-handler
;   [frame handler]
;   (assert (not= frame nil))
;   (let [btn-explain (seesaw.core/select frame [:#explain])]
;     (assert (not= btn-explain nil))
;     (seesaw.core/listen btn-explain :action (fn [e] (handler)))))


(defn set-on-commit-handler
  [frame handler]
  (let [btn-commit (seesaw.core/select frame [:#commit])]
    (seesaw.core/listen btn-commit :action (fn [e] (handler)))))


(defn set-on-rollback-handler
  [frame handler]
  (let [btn-rollback (seesaw.core/select frame [:#rollback])]
    (seesaw.core/listen btn-rollback :action (fn [e] (handler)))))


(defn set-on-execute-handler
  "Set Execute button handler."
  [frame handler]
  (let [btn-execute (seesaw.core/select frame [:#execute])]
    (assert (not= btn-execute nil))
    (seesaw.core/listen btn-execute :action (fn [e] (handler)))))


(defn set-on-new-handler
  "Set New button handler."
  [frame handler]
  (let [btn-new (seesaw.core/select frame [:#new])]
    (assert (not= btn-new nil))
    (seesaw.core/listen btn-new :action (fn [e] (handler)))))


(defn set-on-save-handler
  "Set Save button handler."
  [frame handler]
  (let [btn-save (seesaw.core/select frame [:#save])]
    (assert (not= btn-save nil))
    (seesaw.core/listen btn-save :action (fn [e] (handler)))))


(defn set-on-open-handler
  [frame handler]
  (let [btn-open (seesaw.core/select frame [:#open])]
    (assert (not= btn-open nil))
    (seesaw.core/listen btn-open :action (fn [e] (handler)))))


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
  ^String
  [frame]
  (let [all-text (seesaw.core/value (seesaw.core/select frame [:#sql]))
        caret-position (seesaw.core/config (seesaw.core/select frame [:#sql]) :caret-position)
        all-text-lines (split-lines all-text)
        line-no (get-line-no all-text caret-position)
        [start end] (extend-nonempty-lines-range all-text-lines line-no line-no)
        block-lines (subvec all-text-lines start (inc end))
        block-text (join "\n" block-lines)]
    block-text))


(defn get-contents
  "Get text from worksheet."
  ^String
  [^javax.swing.JFrame frame]
  (assert (not= frame nil))
  (-> (seesaw.core/select frame [:#sql])
      (seesaw.core/value)))


(defn set-on-scroll-handler
  "Add handler to fetch more results on scroll."
  [frame handler]
  (let [scrollable (seesaw.core/select frame [:#results-table-scrollable])
        viewport (.getViewport scrollable)]
    (seesaw.core/listen viewport :change handler)))


(defn get-scroll-position
  "Return scroll position as float."
  [viewport]
  (assert (not= viewport nil))
  (let [view-size (.getViewSize viewport)
        view-rect (.getViewRect viewport)
        view-rect-pos (float (.y view-rect))
        view-height (float (.height view-size))
        view-rect-height (float (.height view-rect))
        view-rect-pos-max (- view-height view-rect-height)
        view-scroll-position (if (<= view-rect-pos-max 0.0) 0.0 (/ view-rect-pos view-rect-pos-max))]
    view-scroll-position))


(defn append-row!
  [^javax.swing.JTable results-table row]
  (let [row-count (seesaw.table/row-count results-table)]
    (seesaw.table/insert-at! results-table row-count row)))


(defn append-rows!
  "Append rows to worksheet results table.

  Params:

  - new-rows is a seq of new rows to be appended."
  [worksheet new-rows]
  (println (format "appending %d rows to table" (count new-rows)))
  (let [frame (:frame @worksheet)
        _ (assert (not= frame nil))
        results-table (seesaw.core/select frame [:#results-table])
        _ (assert (not= results-table nil))]
    (doall (map (partial append-row! results-table) new-rows))))


(defn fetch-more-results!
  "Try to load more results from lazy sequence of results."
  [worksheet]
  (let [result (:result @worksheet)
        _ (assert (not= result nil))
        [strict-rows lazy-rows] (result :rows)
        strict-rows-count (count strict-rows)
        _ (println (format "strict-rows-count: %d" strict-rows-count))
        new-count (+ strict-rows-count 256)
        new-strict-rows (take new-count lazy-rows)
        _ (println (format "new-strict-rows count: %d" (count new-strict-rows)))
        _ (assert (>= (count new-strict-rows) (count strict-rows)))
        new-rows [new-strict-rows lazy-rows]
        new-result (assoc result :rows new-rows)]
    (swap! worksheet assoc :result new-result)
    (let [new-new-rows (nthrest new-strict-rows (count strict-rows))]
      (println (format "new-new-rows count: %d" (count new-new-rows)))
      (append-rows! worksheet new-new-rows))))


(defn on-results-table-scrolled
  [worksheet-atom e]
  (let [frame (:frame @worksheet-atom)
        _ (assert (not= frame nil))
        viewport (.getViewport (seesaw.core/select frame [:#results-table-scrollable]))
        scroll-pos (get-scroll-position viewport)]
    (if (> scroll-pos 0.75)
      (fetch-more-results! worksheet-atom))))


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
  [worksheet-atom columns rows]
  (let [^javax.seing.JFrame frame (:frame @worksheet-atom)
        _ (assert (not= frame nil))
        [strict-rows lazy-rows] rows
        ^javax.swing.JTable results-table (seesaw.core/table :id :results-table
                                                             :auto-resize :off
                                                             :model [:columns columns
                                                                     :rows strict-rows])
        results-table-scrollable (seesaw.core/scrollable results-table :id :results-table-scrollable)
        ^javax.swing.JPanel results-panel (seesaw.core/select frame [:#results-panel])
        to-remove (seesaw.core/select frame [:#results-panel :> :*])]
    (doall (map (partial seesaw.core/remove! results-panel) to-remove))
    (seesaw.core/add! results-panel results-table-scrollable)
    (if (> (count strict-rows) 0)
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
                   (.setPreferredWidth table-column (* column-width 10)))))))
    (set-on-scroll-handler frame (partial on-results-table-scrolled worksheet-atom))))


(defn clear-results!
  "Clear results pane."
  [^javax.swing.JFrame frame]
  (assert (not= frame nil))
  (let [^javax.swing.JPanel results-panel (seesaw.core/select frame [:#results-panel]) 
        to-remove (seesaw.core/select frame [:#results-panel :> :*])]
    (doall (map (partial seesaw.core/remove! results-panel) to-remove))))


(defn choose-save-file
  "Show save dialog, return absolute path as string."
  [frame]
  (-> (seesaw.chooser/choose-file frame :type :save)
      (.getAbsolutePath)))


(defn show-explain-plan!
  "Show explain plan."
  [^javax.swing.JFrame frame
   ^String explain-plan]
  (println "displaying explain plan"))


(defn log
  "Add log message to status panel."
  [^javax.swing.JFrame frame
   ^String message]
  (let [^javax.swing.JContainer log-tab (seesaw.core/select frame [:#log-panel])
        ^javax.swing.JComponent log-text (seesaw.core/select log-tab [:#log])]
    (assert (not= log-tab nil))
    (assert (not= log-text nil))
    (let [^String old-text (seesaw.core/text log-text)
          ^String new-text (str old-text message)]
      (seesaw.core/text! log-text new-text))))


(defn status-text
  "Set status bar text."
  [^javax.swing.JFrame frame
   ^String message]
  (assert (not= frame nil))
  (assert (not= message nil))
  (let [status-bar-text (seesaw.core/select frame [:#status-bar-text])]
    (assert (not= status-bar-text nil))
    (seesaw.core/text! status-bar-text message)))
