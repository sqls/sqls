
(ns sqls.ui
  "Deal with UI"
  (:require seesaw.core)
  (:require [sqls.stor :as stor])
  (:require
    sqls.ui.conn-edit
    sqls.ui.worksheet
    )
  )


(defn show-error!
  "Show error message."
  [msg desc]
  (seesaw.core/alert msg))


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

