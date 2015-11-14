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
  (get-dimensions! [ww])
  (get-split-ratio! [ww])
  (choose-save-file! [ww])
  (show-results! [ww columns rows])
  (select-tab! [ww i])
  (log! [ww s])
  (status-text! [ww s]))


(defprotocol UI
  "Main UI."
  (invoke-later! [ui f])
  (show-error! [ui msg])
  (show-about! [ui text])
  (create-conn-list-window! [ui handlers drivers connections])
  (create-worksheet-window! [ui conn-name worksheet-data]))

