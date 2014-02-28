
(ns sqls.ui.worksheet
  (:require [seesaw.core :as seesaw.core])
  (:require [seesaw.rsyntax :as seesaw.rsyntax]))


(defn on-query-text-area-key-press
  "Handle key press in query text area of SQL Worksheet."
  [e]
)


(defn create-worksheet-frame
  "Create worksheet frame."
  []
  (let [query-text-area (seesaw.rsyntax/text-area :syntax :sql
                                                  :columns 80
                                                  :rows 25
                                                  :listen [:key-pressed on-query-text-area-key-press])
        results-panel (seesaw.core/label "test")
        worksheet-frame (seesaw.core/frame :title "SQL Worksheet"
                                 :content (seesaw.core/vertical-panel :items [query-text-area results-panel]))]
    (seesaw.core/pack! worksheet-frame)
    worksheet-frame
    )
)



