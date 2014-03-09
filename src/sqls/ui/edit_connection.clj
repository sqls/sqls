
(ns sqls.ui.edit-connection
  "UI code for add or edit connection"
  (:use [seesaw.core :only [
                            alert
                            button
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
                            ]]))


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

(defn create-edit-connection-frame
  "Create add-or-edit connection dialog.

  Parameters:

  - conn-list-frame - parent frame that contains list of defined connections,
  - conn-data - if not nil, then data of connection being edited,
  - save! - save handler to be called when user commits edit.
  "
  [conn-list-frame conn-data save! test-conn!]
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
        fields [
                (text :id :name :text conn-name :preferred-size [400 :by 0])
                (text :id :jar :text conn-jar)
                (text :id :class :text conn-class)
                (text :id :jdbc-conn-str :text conn-str)
                (text :id :desc :text conn-desc)
                ]
        buttons [
                 (button :id :cancel :text "Cancel" :listen [:action on-btn-save-connection-cancel])
                 (button :id :test :text "Test" :listen [:action (partial on-btn-test-connection test-conn!)])
                 (button :id :ok :text "Ok" :listen [:action (partial on-btn-save-connection-ok conn-list-frame save! conn-data)])
                 ]
        form-items (interleave labels fields)]
    (assert (= (count labels) (count fields)))
    (println "create-edit-connection-frame")
    (->
      (custom-dialog
          :title "SQLS: Add connection"
          :content (vertical-panel :border 4
                                   :items [
                                           (grid-panel :columns 2
                                                       :items form-items)
                                           (horizontal-panel :id :buttons
                                                             :items buttons)]))
      pack!)))

