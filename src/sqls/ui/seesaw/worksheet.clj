(ns sqls.ui.seesaw.worksheet
  (:use [clojure.string :only (join split-lines trim)])
  (:require seesaw.chooser
            seesaw.core
            seesaw.keymap
            seesaw.keystroke
            seesaw.rsyntax
            seesaw.table)
  (:require [sqls.ui.dev-util :as ui-dev]
            [sqls.ui.proto :refer [UI WorksheetWindow]]
            [sqls.ui.seesaw.commander :refer [show-commander!]]
            [sqls.util :refer [debugf str-or-nil?]])
  (:import [java.awt Component Dimension Font GridBagConstraints GridBagLayout Insets Label Point Rectangle Toolkit]
           [java.awt.event InputEvent KeyEvent]
           [java.io File]
           [javax.swing JComponent JFrame JPanel JScrollPane JTable JViewport KeyStroke JScrollBar]
           [javax.swing.table DefaultTableModel TableColumn]
           [javax.swing.text DefaultEditorKit]
           [org.fife.ui.rsyntaxtextarea RSyntaxTextArea]))

(defn fix-textarea-bindings!
  "Fix rich-text-area bindings.
  Note that it does modify its argument before returning it."
  ^RSyntaxTextArea
  [^RSyntaxTextArea t]
  (let [is-osx (-> (System/getProperty "os.name") (.toLowerCase) (.startsWith "mac os x"))
        ctrl-enter-ks (KeyStroke/getKeyStroke "control pressed ENTER")
        m (.getInputMap t)]
    (when is-osx
        (assert (not (nil? m)))
        (let [default-modifier (-> (Toolkit/getDefaultToolkit) (.getMenuShortcutKeyMask))
              shift InputEvent/SHIFT_MASK]
          (.put m (KeyStroke/getKeyStroke KeyEvent/VK_LEFT (bit-or default-modifier shift)) DefaultEditorKit/selectionBeginLineAction)
          (.put m (KeyStroke/getKeyStroke KeyEvent/VK_RIGHT (bit-or default-modifier shift)) DefaultEditorKit/selectionEndLineAction)
          (.put m (KeyStroke/getKeyStroke KeyEvent/VK_HOME shift) DefaultEditorKit/selectionBeginAction)
          (.put m (KeyStroke/getKeyStroke KeyEvent/VK_END shift) DefaultEditorKit/selectionEndAction)))
    (loop [m m]
      (let [keystrokes (set (.keys m))]
        (if (contains? keystrokes ctrl-enter-ks)
          (.remove m ctrl-enter-ks)
          (when-let [p (.getParent m)]
            (recur p))))))
  t)

(defn on-key-press!
  "Wrap key press handler for sql text area. Calls supplied handler if key matches."
  [handler keystroke e]
  (let [event-key-stroke (KeyStroke/getKeyStrokeForEvent e)]
    (if (= keystroke event-key-stroke)
      (handler))))

(defn set-ctrl-enter-handler!
  "Set Ctrl-Enter handler on frame. Handler should expect no parameters (all needed parameters
  should be baked in using partial or similar means."
  [frame handler]
  (assert (not= frame nil))
  (let [ctrl-enter-keystroke (seesaw.keystroke/keystroke "control ENTER")
        sql-text-area (seesaw.core/select frame [:#sql])]
    (assert (not (nil? sql-text-area)))
    (seesaw.core/listen sql-text-area :key-pressed (partial on-key-press! handler ctrl-enter-keystroke))))

(defn set-menu-p-handler!
  "Frame is worksheet frame (JFrame).
  cmds-fn is commander function, which finds command to execute.
  We handle commander display on UI side.
  Controller side provides cmds-fn, and UI uses that function to find commander action that matches search box."
  [frame cmds-fn]
  {:pre [frame
         cmds-fn
         (ifn? cmds-fn)]}
  (let [menu-p-keystroke (seesaw.keystroke/keystroke "menu P")
        ^JComponent sql-text-area (seesaw.core/select frame [:#sql])
        _ (assert sql-text-area)
        ;; cmds-fn is fn that returns seq of matching cmd. but we'd like to move focus to sql text area.
        ;; and we can't ask source for cmds-fn that do this focus, because whole concept of focus is UI -- our client
        ;; shouldn't have to worry about UI focus.
        ;; so let's wrap cmds-fn with our hijacking fn that also adds requestFocus on sql-text-area…
        focus-cmds-fn (fn [text]
                        (let [cmds (cmds-fn text)]
                          (when cmds
                            ;; orig cmd-fn returned cmds… but we want to return modified cmds…
                            (map (fn [cmd]
                                   (let [orig-fn (:fn cmd)
                                         focus-fn (fn []
                                                    (let [r (orig-fn)]
                                                      (.requestFocus sql-text-area)
                                                      r))]
                                     (assoc cmd :fn focus-fn)))
                                 cmds))))
        ;; this is menu-p handler, invoked by "on-key-press! <fn> menu-p-keystroke"
        menu-p-handler! (fn []
                           (show-commander! frame focus-cmds-fn))]
   (seesaw.core/listen sql-text-area :key-pressed (partial on-key-press! menu-p-handler! menu-p-keystroke))))

(defn set-on-commit-handler!
  [frame handler]
  (let [btn-commit (seesaw.core/select frame [:#commit])]
    (seesaw.core/listen btn-commit :action (fn [_] (handler)))))

(defn set-on-rollback-handler!
  [frame handler]
  (let [btn-rollback (seesaw.core/select frame [:#rollback])]
    (seesaw.core/listen btn-rollback :action (fn [_] (handler)))))

(defn set-worksheet-handlers!
  [^JFrame frame
   handlers]
  {:pre [(:ctrl-enter handlers)
         (:worksheet-commands handlers)
         (:commit handlers)
         (:rollback handlers)
         (:closed handlers)]}
  (set-ctrl-enter-handler! frame (:ctrl-enter handlers))
  (set-menu-p-handler! frame (:worksheet-commands handlers))
  (set-on-rollback-handler! frame (:rollback handlers))
  (set-on-commit-handler! frame (:commit handlers))
  (seesaw.core/listen frame :window-closed (fn [_] ((:closed handlers)))))

(defn get-contents!
  "Get text from worksheet."
  ^String
  [^JFrame frame]
  (assert (not= frame nil))
  (-> (seesaw.core/select frame [:#sql])
      (seesaw.core/value)))

(defn get-dimensions!
  [^JFrame frame]
  {:pre [frame]
   :post [(map? %)]}
  ;Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
  (let [screen-size (let [^Dimension dim (-> frame
                                             .getToolkit
                                             .getScreenSize)
                          w (.width dim)
                          h (.height dim)]
                      [w h])
        size (let [^Dimension dim (.getSize frame)]
               [(.width dim) (.height dim)])
        position (let [^Point p (.getLocationOnScreen frame)
                       x (.x p)
                       y (.y p)]
                   [x y])]
    {:position position
     :size size}))

(defn get-split-ratio!
  [^JFrame f]
  (let [splitter (seesaw.core/select f [:#splitter])
        divider-location (seesaw.core/config splitter :divider-location)
        splitter-height (seesaw.core/height splitter)
        divider-size (seesaw.core/config splitter :divider-size)]
    (/ divider-location (- splitter-height divider-size))))

(defn get-line-no
  "Get line number from text and index."
  [text position]
  (let [before-position-text (subs text 0 position)
        is-newline (fn [c] (= (str c) "\n"))]
    (count (filter is-newline before-position-text))))

(defn extend-nonempty-lines-range
  "Return a range (that is, pair of line numbers) that include adjacent non-empty lines.

  First see if start can be decreased, and if it can, then return result of calling recursively with decreased start.
  Otherwise see if end can be increased, and again if it can, then return result of calling recursively with increased end.
  Finally if none of above worked, just return [start end].
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

(defn get-sql!
  "Extract current SQL statement text from frame."
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

(defn get-word!
  "Extract current word from text from frame."
  [frame]
  (let [sql-comp (seesaw.core/select frame [:#sql])
        all-text (seesaw.core/value sql-comp)
        caret-position (seesaw.core/config sql-comp :caret-position)
        word-char? (fn [c]
                     (and (not= c \space)
                          (not= c \;)))
        word (apply str (concat
                          (->> all-text
                               (take caret-position)
                               reverse
                               (take-while word-char?)
                               reverse)
                          (->> all-text
                               (drop caret-position)
                               (take-while word-char?))))]
    word))

(defn log!
  "Add log message to log panel."
  [^JFrame frame
   ^String message]
  (let [log-tab (seesaw.core/select frame [:#log-panel])
        log-text (seesaw.core/select log-tab [:#log])]
    (assert (not= log-tab nil))
    (assert (not= log-text nil))
    (let [^String old-text (seesaw.core/text log-text)
          ^String new-text (str old-text message)]
      (seesaw.core/text! log-text new-text))
    (seesaw.core/scroll! log-text :to :bottom)))

(defn status-text!
  "Set status bar text."
  [^JFrame frame
   ^String message]
  (assert (not= frame nil))
  (assert (not= message nil))
  (let [status-bar-text (seesaw.core/select frame [:#status-bar-text])]
    (assert (not= status-bar-text nil))
    (seesaw.core/text! status-bar-text message)))

(defn status-right-text!
  "Set right panel status bar text."
  [^JFrame frame message]
  {:pre [(instance? JFrame frame)
         (string? message)]}
  (seesaw.core/text!
    (seesaw.core/select frame [:#status-bar-right-text])
    message))

(defn set-on-scroll-handler!
  "Add handler to fetch more results on scroll."
  [^JFrame frame handler]
  {:pre [frame
         handler]}
  (let [^JScrollPane scrollable (seesaw.core/select frame [:#results-table-scrollable])
        ^JViewport viewport (.getViewport scrollable)]
    (seesaw.core/listen viewport :change handler)))

(defn get-scroll-position!
  "Return scroll position as float."
  [^JViewport viewport]
  (let [^Dimension view-size (.getViewSize viewport)
        ^Rectangle view-rect (.getViewRect viewport)
        view-rect-pos (float (.y view-rect))
        view-height (float (.height view-size))
        view-rect-height (float (.height view-rect))
        view-rect-pos-max (- view-height view-rect-height)
        view-scroll-position (if (<= view-rect-pos-max 0.0) 0.0 (/ view-rect-pos view-rect-pos-max))]
    view-scroll-position))

(defn append-row!
  [^DefaultTableModel results-table-model
   row]
  (let [^objects row-array (into-array Object row)]
    (.addRow results-table-model row-array)))

(defn append-rows!
  "Append rows to worksheet results table.
  Params:
  - new-rows is a seq of new rows to be appended."
  [^JTable results-table
   new-rows]
  (let [results-table-model (.getModel results-table)]
    (doseq [row new-rows]
      (append-row! results-table-model row))))

;; This needs to be optimized.
(defn fetch-more-results!
  "Try to load more results from lazy sequence of results."
  [^JTable results-table
   results-atom]
  (let [[strict-rows lazy-rows] @results-atom
        strict-rows-count (count strict-rows)
        new-count (+ strict-rows-count 256)
        new-strict-rows (take new-count lazy-rows)
        _ (assert (>= (count new-strict-rows) (count strict-rows)))
        new-rows [new-strict-rows lazy-rows]]
    (reset! results-atom new-rows)
    (let [new-new-rows (nthrest new-strict-rows (count strict-rows))]
      (append-rows! results-table new-new-rows))))

(defn on-results-table-scrolled!
  [^JScrollPane results-table-scroll-pane
   ^JTable results-table
   results-atom
   _event]
  (let [^JViewport viewport (.getViewport results-table-scroll-pane)
        scroll-pos (get-scroll-position! viewport)]
    (when (> scroll-pos 0.75)
      (fetch-more-results! results-table results-atom))))

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
  {:pre [frame
         (sequential? rows)
         (= 2 (count rows))]}
  (let [[strict-rows _lazy-rows] rows
        ^objects j-columns (into-array Object columns)
        j-rows (into-array (Class/forName "[Ljava.lang.Object;")
                           (for [row strict-rows]
                             (into-array Object row)))
        ^DefaultTableModel table-model (DefaultTableModel. j-rows j-columns)
        ^JTable results-table (JTable. table-model)
        _ (.putClientProperty results-table
                              :seesaw.core/seesaw-widget-id
                              :results-table)
        _ (.setAutoResizeMode results-table JTable/AUTO_RESIZE_OFF)
        ^JScrollPane results-table-scroll-pane (JScrollPane. results-table
                                                             JScrollPane/VERTICAL_SCROLLBAR_ALWAYS
                                                             JScrollPane/HORIZONTAL_SCROLLBAR_ALWAYS)
        _ (.putClientProperty results-table-scroll-pane
                              :seesaw.core/seesaw-widget-id
                              :results-table-scrollable)
        _ (let [scrollbar (.getHorizontalScrollBar results-table-scroll-pane)]
            (.setBlockIncrement scrollbar 100)
            (.setUnitIncrement scrollbar 20))
        ^JPanel results-panel (seesaw.core/select frame [:#results-panel])
        to-remove (seesaw.core/select frame [:#results-panel :> :*])]
    (doall (map (partial seesaw.core/remove! results-panel) to-remove))
    (seesaw.core/add! results-panel results-table-scroll-pane)
    (if (> (count strict-rows) 0)
      (let [table-column-model (.getColumnModel results-table)
            column-count (count columns)
            max-column-widths (vec (for [column-index (range column-count)]
                                     (max 4
                                          (let [column-values (map str (map #(get % column-index) strict-rows))
                                                column-value-lengths (map count column-values)
                                                max-column-value-length (apply max column-value-lengths)]
                                            max-column-value-length))))]
        (doall (for [column-index (range column-count)]
                 (let [^TableColumn table-column (.getColumn table-column-model column-index)
                       column-width (get max-column-widths column-index)]
                   (.setPreferredWidth table-column (* column-width 10)))))))
    ;; pass atom to scroll-handler - they will alter it when reading more rows from lazy seq part
    (set-on-scroll-handler! frame (partial on-results-table-scrolled!
                                           results-table-scroll-pane
                                           results-table
                                           (atom rows)))))

(defn clear-results!
  "Clear results pane."
  [^JFrame frame]
  {:pre [(instance? JFrame frame)]}
  (let [^JPanel results-panel (seesaw.core/select frame [:#results-panel])
        to-remove (seesaw.core/select frame [:#results-panel :> :*])]
    (doall (map (partial seesaw.core/remove! results-panel) to-remove))))


(defn select-tab!
  [^JFrame frame
   idx]
  {:pre [frame
         (integer? idx)]}
  (seesaw.invoke/invoke-now
    (-> (seesaw.core/select frame [:#tabs])
        (seesaw.core/selection! idx))))

(defn release-resources!
  "Detach all components from frame to allow them to be garbage collected.
  This is a workaround of JRE's bug: https://bugs.openjdk.java.net/browse/JDK-8029147"
  [^JFrame frame]
  {:pre [(not (nil? frame))]}
  (.setLayout frame nil)
  (when-let [p (.getContentPane frame)]
    (.removeAll p))
  (.removeAll frame))

(defn change-font-size!
  [^Component component change]
  (let [^Font f (.getFont component)
        current-size (.getSize f)
        new-size (float (+ current-size change))
        new-font (.deriveFont f new-size)]
    (debugf "current size: %s, new size: %s" current-size new-size)
    (when (pos? new-size)
      (.setFont component new-font))))

(defn setup-font-size-keys!
  [frame]
  (let [sql-text-area (seesaw.core/select frame [:#sql])
        log-text (seesaw.core/select frame [:#log])
        plus-key-stroke (seesaw.keystroke/keystroke "menu EQUALS")
        minus-key-stroke (seesaw.keystroke/keystroke "menu MINUS")
        handler-plus (fn []
                       (change-font-size! sql-text-area 1)
                       (change-font-size! log-text 1))
        handler-minus (fn []
                        (change-font-size! sql-text-area -1)
                        (change-font-size! log-text -1))]
    (assert (not (nil? sql-text-area)))
    (seesaw.core/listen sql-text-area :key-pressed (partial on-key-press! handler-plus plus-key-stroke))
    (seesaw.core/listen sql-text-area :key-pressed (partial on-key-press! handler-minus minus-key-stroke))))

(defn create-status-bar-panel!
  []
  (let [panel (JPanel.)
        grid-bag-layout (GridBagLayout.)
        grid-bag-constraints (GridBagConstraints.)
        insets (Insets. 2 2 2 2)
        _ (set! (.-fill grid-bag-constraints) GridBagConstraints/HORIZONTAL)
        _ (set! (.-insets grid-bag-constraints) insets)
        ^Label label-left (seesaw.core/label :id :status-bar-text :text "Hello")
        ^Label label-right (seesaw.core/label :id :status-bar-right-text :h-text-position :right)
        _ (.setLayout panel grid-bag-layout)
        _ (set! (.-weightx grid-bag-constraints) 0.8)
        _ (.add panel label-left grid-bag-constraints)
        _ (set! (.-weightx grid-bag-constraints) 0.2)
        _ (.add panel label-right grid-bag-constraints)]
    panel))

(defn create-worksheet-window!
  "Create implementation of sqls.ui.proto.WorksheetWindow interface.

  Parameters:

  - ui,
  - conn-name,
  - cmds - commander fn,
  - worksheet-data - a dictionary that contains worksheet data saved between runs:
    - :contents - worksheet text area contents,
    - :split-ratio - divider location for main text area and results split pane (relative, from 0.0 to 1.0).

  Frame contains following widgets (among others):

  - :sql - text area with SQL code,
  - :results-panel - container that contains results, which in turn contains table with :id :results,
    contents of this panel are meant to be replaced on each query execution.
  "
  [ui
   ^String conn-name
   worksheet-data]
  {:pre [(string? conn-name)
         (or (nil? worksheet-data) (map? worksheet-data))
         (str-or-nil? (:contents worksheet-data))]}
  (let [contents (:contents worksheet-data)
        clip-to-range (fn
                        [x _min _max]
                        (cond
                          (> x _max) _max
                          (< x _min) _min
                          :else x))
        split-ratio (-> (get worksheet-data :split-ratio 0.5)
                        (clip-to-range 0.1 0.9))
        _ (assert (>= split-ratio 0.0))
        _ (assert (<= split-ratio 1.0))
        ;dimensions (:dimensions worksheet-data)
        ;pref-size (if dimensions
        ;            (let [[_ _ w h] dimensions]
        ;              [h :by w]))
        ;_ (println (format "pref size: %s" pref-size))
        query-text-area (fix-textarea-bindings! (seesaw.rsyntax/text-area :id :sql
                                                                          :syntax :sql
                                                                          :columns 80
                                                                          :rows 25))
        _ (.setTabSize query-text-area 4)
        _ (.setTabsEmulated query-text-area true)
        query-text-area-scrollable (seesaw.core/scrollable query-text-area)
        results-panel (seesaw.core/vertical-panel :id :results-panel
                                                  :preferred-size [800 :by 400])
        log-text (seesaw.core/text :id :log
                                   :multi-line? true
                                   :editable? false
                                   :font (seesaw.font/font :monospaced))
        log-panel (seesaw.core/vertical-panel :id :log-panel
                                              :items [(seesaw.core/scrollable log-text
                                                                              :id :log-scrollable)])
        tabs-panel (seesaw.core/tabbed-panel :id :tabs
                                             :tabs [{:title "Results" :content results-panel}
                                                    {:title "Log" :content log-panel}])
        menu-panel (seesaw.core/horizontal-panel :id :menu-panel
                                                 :items [
                                                         ; (seesaw.core/button :id :new
                                                         ;                     :icon (seesaw.icon/icon "new.png"))
                                                         ; (seesaw.core/button :id :save
                                                         ;                     :icon (seesaw.icon/icon "floppy.png"))
                                                         ; (seesaw.core/button :id :open
                                                         ;                     :icon (seesaw.icon/icon "open.png"))
                                                         ; (seesaw.core/button :id :explain
                                                         ;                     :text "Explain plan")
                                                         ; (seesaw.core/button :id :execute
                                                         ;                     :text "Execute")
                                                         (seesaw.core/button :id :commit
                                                                             :text "Commit")
                                                         (seesaw.core/button :id :rollback
                                                                             :text "Rollback")
                                                         ; (seesaw.core/button :id :print-ui-tree
                                                         ;                     :text "Dump UI Tree")
                                                         ])
        center-panel (seesaw.core/top-bottom-split query-text-area-scrollable tabs-panel
                                                   :id :splitter
                                                   :divider-location split-ratio)
        south-panel (create-status-bar-panel!)
        border-panel (seesaw.core/border-panel :north menu-panel
                                               :center center-panel
                                               :south south-panel)
        worksheet-frame (seesaw.core/frame
                          :title (format "SQL Worksheet: %s" conn-name)
                          :content border-panel
                          :on-close :dispose)]
    (seesaw.core/pack! worksheet-frame)
    (if contents
      (seesaw.core/config! query-text-area :text contents))
    ; (let [btn-print-ui-tree (seesaw.core/select worksheet-frame [:#print-ui-tree])]
    ;   (if btn-print-ui-tree
    ;     (seesaw.core/listen
    ;       btn-print-ui-tree :action
    ;       (fn [_] (ui-dev/print-ui-tree worksheet-frame)))))
    (setup-font-size-keys! worksheet-frame)
    (seesaw.keymap/map-key worksheet-frame
                           (seesaw.keystroke/keystroke "menu W")
                           (fn [_] (seesaw.core/dispose! worksheet-frame))
                           :scope :global)
    (reify WorksheetWindow
      (show-worksheet-window! [_] (do
                                    (seesaw.core/show! worksheet-frame)
                                    (let [^JComponent sql (seesaw.core/select worksheet-frame [:#sql])]
                                      (assert sql)
                                      (.requestFocus sql))))
      (set-worksheet-handlers! [_ handlers] (set-worksheet-handlers! worksheet-frame handlers))
      (get-contents! [_] (get-contents! worksheet-frame))
      (get-dimensions! [_] (get-dimensions! worksheet-frame))
      (get-split-ratio! [_] (get-split-ratio! worksheet-frame))
      (get-sql! [_] (get-sql! worksheet-frame))
      (get-word! [_] (get-word! worksheet-frame))
      (log! [_ t] (log! worksheet-frame t))
      (status-text! [_ t] (status-text! worksheet-frame t))
      (status-right-text! [_ t] (status-right-text! worksheet-frame t))
      (show-results! [_ c r] (show-results! worksheet-frame c r))
      (clear-results! [_] (clear-results! worksheet-frame))
      (select-tab! [_ i] (select-tab! worksheet-frame i))
      (release-resources! [_] (release-resources! worksheet-frame)))))

(defn dispose-worksheet-frame!
  "Dispose worksheet frame."
  [frame]
  (seesaw.core/dispose! frame))

(defn set-on-execute-handler
  "Set Execute button handler."
  [frame handler]
  (let [btn-execute (seesaw.core/select frame [:#execute])]
    (assert (not= btn-execute nil))
    (seesaw.core/listen btn-execute :action (fn [_] (handler)))))

(defn set-on-new-handler
  "Set New button handler."
  [frame handler]
  (let [btn-new (seesaw.core/select frame [:#new])]
    (assert (not= btn-new nil))
    (seesaw.core/listen btn-new :action (fn [_] (handler)))))

(defn set-on-save-handler
  "Set Save button handler."
  [frame handler]
  (let [btn-save (seesaw.core/select frame [:#save])]
    (assert (not= btn-save nil))
    (seesaw.core/listen btn-save :action (fn [_] (handler)))))

(defn set-on-open-handler
  [frame handler]
  (let [btn-open (seesaw.core/select frame [:#open])]
    (assert (not= btn-open nil))
    (seesaw.core/listen btn-open :action (fn [_] (handler)))))

(defn get-worksheet-frame-dimensions
  "Get worksheet frame position and size."
  [^JFrame frame]
  (let [^Dimension s (seesaw.core/config frame :size)
        w (.width s)
        h (.height s)
        ^Point p (.getLocationOnScreen frame)
        x (.x p)
        y (.y p)]
    [x y w h]))

(defn choose-save-file
  "Show save dialog, return absolute path as string."
  [frame]
  (-> (^File seesaw.chooser/choose-file frame :type :save)
      (.getAbsolutePath)))

(defn show-explain-plan!
  "Show explain plan."
  [^JFrame frame
   ^String explain-plan]
  (println "displaying explain plan"))
