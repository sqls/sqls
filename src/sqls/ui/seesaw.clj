(ns sqls.ui.seesaw
  (:require [seesaw.core :refer [native!]]
            [sqls.ui.proto :refer [ConnListWindow
                                   WorksheetWindow
                                   UI]]
            [sqls.ui.seesaw.conn-list :as conn-list]
            sqls.ui.seesaw.conn-edit
            sqls.ui.seesaw.worksheet)
  (:import [java.awt.event ActionListener]
           [javax.swing Timer]))

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

(defn create-timer!
  "Create and start timer, return it.
  - timer-interval - timer interval in ms,
  - f - function of no params to call in timer."
  [timer-interval f]
  {:pre [(pos? timer-interval)
         (ifn? f)]}
  (let [action-listener (reify ActionListener
                          (actionPerformed [_ e] (f)))
        timer (Timer. timer-interval action-listener)]
    (.start timer)
    timer))

(defn destroy-timer!
  [timer]
  (println (format "stopping timer %s" timer))
  (.stop timer))

(defn create-ui!
  "Create UI."
  [about-text]
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
    (create-timer! [_ interval f] (create-timer! interval f))
    (destroy-timer! [_ timer] (destroy-timer! timer))
    (create-conn-list-window!
      [ui drivers plugins handlers connections]
      (assert (sequential? plugins))
      (assert (coll? handlers))
      (assert (not (nil? (:create-worksheet handlers))))
      (conn-list/create-conn-list-window! ui about-text drivers plugins handlers connections))
    (create-worksheet-window!
      [ui conn-name worksheet-data]
      (sqls.ui.seesaw.worksheet/create-worksheet-window! ui conn-name worksheet-data))))

