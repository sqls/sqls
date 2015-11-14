(ns sqls.ui.seesaw.conn-edit
  "UI code for add or edit connection"
  (:use [seesaw.core :only [
                            alert
                            button
                            combobox
                            custom-dialog
                            dispose!
                            grid-panel
                            horizontal-panel
                            label
                            pack!
                            select
                            text
                            text!
                            to-root
                            value
                            vertical-panel
                            ]])
  (:require seesaw.mig
            [sqls.util :refer [infof spy]]))


(defn on-btn-save-connection-cancel
  "Just close the dialog"
  [e]
  (dispose! e))


(defn get-conn-data
  "Extract conn data from dialog."
  [root]
  (let [conn-name (value (select root [:#name]))
        conn-jar (value (select root [:#jar]))
        conn-class (value (select root [:#class]))
        conn-jdbc-conn-str (value (select root [:#jdbc-conn-str]))
        conn-desc (value (select root [:#desc]))]
    {"name" conn-name
     "jar" conn-jar
     "class" conn-class
     "jdbc-conn-str" conn-jdbc-conn-str
     "desc" conn-desc}))


(defn on-btn-save-connection-ok
  "Handle Save button click in edit-connection dialog.

  All but last parameter should be provided by wrapping this function with partial.
  "
  [conn-list-frame save! old-conn-data e]
  (let [root (to-root e)
        conn-data (get-conn-data root)]
    (save! conn-list-frame old-conn-data conn-data)
    (dispose! e)))


(defn show-test-success!
  "Display message indicating successful connection test."
  [frame]
  (alert frame "OK"))


(defn show-test-failure!
  [frame message]
  (alert frame (format "Connection failed:\n%s" message)))


(defn on-btn-test-connection
  "Handle \"Test\" button click.

  First parameter is a function that returns a struct with following keys:

  - :ok - if this is true, then connection was established,
  - :desc - optional message describing what's wrong with connection parameters.

  Second parameter is an event.

  This function is meant to be wrapped in partial, because event handlers
  take only one parameter.
  "
  [test-conn! e]
  (let [conn-data (get-conn-data (to-root e))
        result (test-conn! conn-data)]
    (if (:ok result)
      (show-test-success! (to-root e))
      (show-test-failure! (to-root e) (:desc result)))))


(defn create-edit-connection-frame!
  "Create add-or-edit connection dialog.

  Parameters:

  - conn-list-frame - parent frame that contains list of defined connections,
  - conn-data - if not nil, then data of connection being edited,
  - drivers - list of JDBC driver class names to populate dropdown,
  - save! - save handler to be called when user commits edit,
  - test-conn! - function that tests conn.
  "
  [conn-list-frame conn-data drivers save! test-conn!]
  {:pre [(let [_ (infof "drivers: %s" (into [] drivers))]
           (or
             (nil? (spy "drivers" drivers))
             (and
               (coll? drivers)
               (not (empty? drivers))
               (every? sequential? drivers))))]}
  (let [conn-name (get conn-data "name")
        conn-jar (get conn-data "jar")
        conn-class (get conn-data "class")
        conn-str (get conn-data "jdbc-conn-str")
        conn-desc (get conn-data "desc")
        label-texts ["Name"
                     "Driver JAR file (optional)"
                     "Driver Class"
                     "JDBC Connection String"
                     "Description (optional)"]
        labels (map #(label :text % :border 2 :preferred-size [100 :by 0]) label-texts)
        default-field-options {:preferred-size [0 :by 400]}
        fields [(text :id :name :text conn-name :preferred-size [400 :by 0])
                (text :id :jar :text conn-jar)
                (if drivers
                  (let [model (map first drivers)                          ; for now only classes
                        _combobox (combobox :id :class
                                            :editable? true
                                            :model model)]
                    (seesaw.core/selection! _combobox (or conn-class ""))
                    _combobox)
                  (text :id :class :text conn-class))
                (text :id :jdbc-conn-str :text conn-str)
                (text :id :desc :text conn-desc)]
        buttons [(button :id :cancel :text "Cancel" :listen [:action on-btn-save-connection-cancel])
                 (button :id :test :text "Test" :listen [:action (partial on-btn-test-connection test-conn!)])
                 (button :id :ok :text "Ok" :listen [:action (partial on-btn-save-connection-ok conn-list-frame save! conn-data)])]
        label-to-mig-panel-item (fn [w] [w])
        field-to-mig-panel-item (fn [w] [w "grow, wrap"])
        label-mig-panel-items (map label-to-mig-panel-item labels)
        field-mig-panel-items (map field-to-mig-panel-item fields)
        button-mig-panel-items [[(horizontal-panel :items buttons) "span 2, align right"]] ; only one item that contains vertical-panel and spans all columns
        mig-panel-items (concat (interleave label-mig-panel-items field-mig-panel-items) button-mig-panel-items)
        mig-panel-constraints ["fillx" "[grow 0][grow]"]]
    (assert (= (count labels) (count fields)))
    ; (println "mig panel items:" mig-panel-items)
    (->
      (custom-dialog :title "SQLS: Add connection"
                     :content (seesaw.mig/mig-panel :items mig-panel-items
                                                    :constraints mig-panel-constraints))
          ; :content (vertical-panel :border 4
          ;                          :items [
          ;                                  (grid-panel :columns 2
          ;                                              :items form-items)
          ;                                  (horizontal-panel :id :buttons
          ;                                                    :items buttons)]))
      pack!)))

