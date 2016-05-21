(ns sqls.ui.proto
  "SQLs UI protocols.")


(defprotocol ConnListWindow
  (show-conn-list-window! [win])
  (enable-conn! [win conn-name])
  (disable-conn! [win conn-name])
  (set-conns! [win conns]))


(defprotocol WorksheetWindow
  "Worksheet Window."
  (show-worksheet-window! [ww])
  (set-worksheet-handlers! [ww handlers])
  (get-contents! [ww])
  (get-sql! [ww])
  (get-word! [ww])
  (get-dimensions! [ww])
  (get-split-ratio! [ww])
  (choose-save-file! [ww])
  (show-results! [ww columns rows])
  (clear-results! [ww])
  (select-tab! [ww i])
  (log! [ww s])
  (status-text! [ww s])
  (status-right-text! [ww s])
  ;; This is to workaround bug on OS X where Frames are never garbage collected.
  ;; The Frame itself is usually small, but for example Rich Text Area can reference
  ;; significant amount of memory.
  (release-resources! [ww]))


(defprotocol UI
  "Main UI."
  (invoke-later! [ui f])
  (show-error! [ui msg])
  (show-about! [ui text])
  (create-timer! [ui interval f])
  (destroy-timer! [ui timer])
  (create-conn-list-window! [ui handlers drivers plugins connections])
  (create-worksheet-window! [ui conn-name worksheet-data]))

