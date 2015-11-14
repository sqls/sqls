(ns sqls.ui.seesaw
  (:require [seesaw.core :refer [native!]]
            [sqls.ui.proto :refer [ConnListWindow
                                   WorksheetWindow
                                   UI]]
            [sqls.ui.seesaw.conn-list :as conn-list]
            sqls.ui.seesaw.conn-edit
            sqls.ui.seesaw.worksheet))


(defn show-about-dialog!
  [^String text]
  (let [text-field (seesaw.core/text :text text
                                     :margin 2
                                     :wrap-lines? true
                                     :editable? false
                                     :multi-line? true)
        dialog (seesaw.core/custom-dialog :title "SQLs About"
                                          :content (seesaw.core/scrollable text-field
                                                                           :minimum-size [160 :by 120]
                                                                           :preferred-size [640 :by 480]))]
    (seesaw.core/pack! dialog)
    (seesaw.core/scroll! text-field :to :top)
    (seesaw.core/show! dialog)))

(defn create-ui!
  "Create UI.
  Handlers is a map that contains handlers for high level events:
  - last-window-closed - called when last window is closed."
  [about-text handlers]
  (let [windows (atom nil)]
    (native!)
    (reify
      Object
      (toString
        [_]
        (format "Seesaw UI"))

      UI
      (invoke-later!
        [_ f]
        (seesaw.core/invoke-later f))
      (show-error!
        [_ msg]
        (seesaw.core/alert msg))
      (show-about!
        [_ text]
        (show-about-dialog! text))
      (create-conn-list-window!
        [ui drivers handlers connections]
        (assert (coll? handlers))
        (assert (not (nil? (:create-worksheet handlers))))
        (conn-list/create-conn-list-window! ui about-text drivers handlers connections))
      (create-worksheet-window!
        [ui conn-name worksheet-data]
        (sqls.ui.seesaw.worksheet/create-worksheet-window! ui conn-name worksheet-data)))))

