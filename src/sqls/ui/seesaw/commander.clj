(ns sqls.ui.seesaw.commander
  "Support for command window."
  (:require fipp.edn
            seesaw.core
            seesaw.table
            [sqls.util :refer [infof]])
  (:import (javax.swing JComponent JFrame JPanel KeyStroke Box JScrollPane ListSelectionModel JTable)
           (java.awt Component BorderLayout)
           (java.awt.event MouseAdapter InputEvent)))

(defn display-results!
  [cmd-atom
   glass
   text
   results]
  {:pre [(map? @cmd-atom)
         (string? text)
         (sequential? results)
         (every? :text results)
         (every? :fn results)]}
  #_ (infof "displaying results: %s" (with-out-str (fipp.edn/pprint results)))
  (let [rows (map (fn [result]
                     {:command (:text result)
                      :fn (:fn result)})
                   results)
        table (seesaw.core/select glass [:#results-table])]
    (assert (not (nil? table)))
    (seesaw.table/clear! table)
    (when (and rows (not (empty? rows)))
      (let [to-add (interleave (repeat 0) rows)]
        #_ (infof "to-add: %s" (with-out-str (fipp.edn/pprint to-add)))
        (apply seesaw.table/insert-at! table to-add)))))

(defn move-selection!
  [^ListSelectionModel selection-model
   offset
   limit]
  (let [current (.getMinSelectionIndex selection-model)
        new (let [m (+ current offset)]
              (cond
                (neg? m) 0
                (>= m limit) (dec limit)
                :else m))]
    (.setSelectionInterval selection-model 0 new)))

(defn run-command!
  [cmd-atom
   ^JComponent glass]
  {:pre [cmd-atom
         (map? @cmd-atom)
         glass]}
  (let [^JTable results-table (seesaw.core/select glass [:#results-table])
        _ (assert results-table)
        ^ListSelectionModel selection-model (.getSelectionModel results-table)
        current-idx (let [i (.getMinSelectionIndex selection-model)]
                      (if (neg? i) 0 i))
        item (seesaw.table/value-at results-table current-idx)]
    (when item
      (assert (map? item))
      (assert (:command item))
      (assert (ifn? (:fn item)))
      (let [cmd-fn! (:fn item)]
        (.setVisible glass false)
        ;; TODO: do not run in UI thread
        (cmd-fn!)))))

(defn on-key-released!
  [^JFrame frame
   cmds-fn
   cmd-atom
   ^JComponent glass
   e]
  {:pre [frame
         (ifn? cmds-fn)
         cmd-atom
         (map? @cmd-atom)
         glass]}
  (let [^JTable table (seesaw.core/select glass [:#results-table])
        count (seesaw.table/row-count table)
        selection-model (.getSelectionModel table)
        event-ks (KeyStroke/getKeyStrokeForEvent e)
        esc-ks (seesaw.keystroke/keystroke "released ESCAPE")
        enter-ks (seesaw.keystroke/keystroke "released ENTER")
        down-arrow-ks (seesaw.keystroke/keystroke "released DOWN")
        up-arrow-ks (seesaw.keystroke/keystroke "released UP")
        ]
    ; (infof "event-ks: %s" event-ks)
    (cond
      (= event-ks esc-ks) (.setVisible glass false)
      (= event-ks up-arrow-ks) (move-selection! selection-model -1 count)
      (= event-ks down-arrow-ks) (move-selection! selection-model 1 count)
      (= event-ks enter-ks) (run-command! cmd-atom glass)
      :else (let [txt (:text @cmd-atom)
                  input (seesaw.core/select glass [:#input])
                  new-txt (seesaw.core/text input)]
              (when (not= txt new-txt)
                ;; text changed since last time: query cmds and display results
                ;; TODO: do not run cmds-fn in ui thread
                (let [results (cmds-fn new-txt)]
                  (swap! cmd-atom assoc :text new-txt)
                  (display-results! cmd-atom glass new-txt results)))))))

(defn show-commander!
  "Create and show command window.
  Command UI is Glass Pane and will replace existing glass pane.
  Params:
  - parent frame - where to show modal cmd window,
  - cmds-fn - fn to search for commands; can have side-effects."
  [^JFrame parent
   cmds-fn]
  {:pre [parent
         cmds-fn
         (ifn? cmds-fn)]}
  (let [cmd-atom (atom {:text ""})
        ^Component t (seesaw.core/text :id :input)     ; input
        ^JPanel cmds-box (JPanel. (BorderLayout.))     ; contains input and results
        ^JTable results-table (seesaw.core/table :id :results-table
                                                 :model [:columns [{:key :command :text ""}]
                                                 :rows []])
        results-table-selection-model (.getSelectionModel results-table)
        _ (.setSelectionMode results-table-selection-model ListSelectionModel/SINGLE_SELECTION)
        ^JScrollPane results-scrollable (seesaw.core/scrollable results-table)
        ;; main glass box
        ^Box glass (Box/createHorizontalBox)
        ]
    ;; main box intercepts mouse events
    (.addMouseListener glass (proxy [MouseAdapter] []
                               (mouseClicked [^InputEvent e]
                                 ; (infof "mouse clicked on glass")
                                 (.consume e))
                               (mousePressed [^InputEvent e]
                                 ; (infof "mouse pressed on glass")
                                 (.consume e))))
    ;; input box handles additional keys
    (seesaw.core/listen t :key-released (partial on-key-released! parent cmds-fn cmd-atom glass))
    ;; central box contains text input at first
    (.add cmds-box t (BorderLayout/NORTH))
    (.add cmds-box results-scrollable)
    ;; glass box has three children: two fake components for margins, and central box in center
    (.add glass (Box/createHorizontalStrut 100))
    (.add glass cmds-box)
    (.add glass (Box/createHorizontalStrut 100))
    ;; add this glass as glass pane to parent
    (.setGlassPane parent glass)
    ;; show it
    (.setVisible glass true)
    ;; initialize results
    ;; TODO: not in UI thread
    (let [results (cmds-fn "")]
      (display-results! cmd-atom glass "" results))
    ;; and finally put focus in input field
    (seesaw.core/request-focus! t)))
