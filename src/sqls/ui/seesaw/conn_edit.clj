(ns sqls.ui.seesaw.conn-edit
  "UI code for add or edit connection"
  (:require
    [clojure.string :refer [blank?]]
    [seesaw.core :refer [
                         alert
                         button
                         combobox
                         custom-dialog
                         dispose!
                         grid-panel
                         horizontal-panel
                         label
                         listen
                         pack!
                         select
                         selection
                         text
                         text!
                         to-root
                         value
                         vertical-panel
                         ]]
    [seesaw.font :refer [font]]
    seesaw.mig
    [sqls.model :refer [->conn]]
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
    (->conn conn-name
            conn-desc
            conn-class
            conn-jar
            conn-jdbc-conn-str)))

(defn on-btn-save-connection-ok!
  "Handle Save button click in edit-connection dialog.
  All but last parameter should be provided by wrapping this function with partial."
  [save! old-conn-data e]
  {:pre [(ifn? save!)]}
  (let [root (to-root e)
        conn-data (get-conn-data root)]
    (save! old-conn-data conn-data)
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

(defn get-jdbc-url-template
  [plugins class-name]
  {:pre [(sequential? plugins)
         (string? class-name)]}
  (let [cls-plugin-seq (apply concat (map (fn [plugin]
                                            (if-let [plugin-classes (if-let [clfn (:classes plugin)]
                                                                      (clfn))]
                                              ;; plugin classes are all classes of plugin, each class is really pair
                                              ;; so we turn it into seq of [class-name plugin]
                                              (map (fn [plugin-class-pair]
                                                     [(first plugin-class-pair)
                                                      plugin])
                                                   plugin-classes)
                                              []))
                                          plugins))
        cls-plugin-map (into {} cls-plugin-seq)]
    (assert (map? cls-plugin-map))
    (assert (every? string? (keys cls-plugin-map)))
    (assert (every? map? (vals cls-plugin-map)))
    (when-let [class-plugin (cls-plugin-map class-name)]
      (:jdbc-url-template class-plugin))))

(defn on-combo-action!
  [plugins e]
  {:pre [(not (nil? e))]}
  (when-let [r (to-root e)]
    (let [jdbc-conn-str-tmpl-label (select r [:#jdbc-conn-str-tmpl])
          combo (select r [:#class])
          sel (selection combo)]
      (when sel
        (when-let [tmpl (get-jdbc-url-template plugins sel)]
          (text! jdbc-conn-str-tmpl-label tmpl))))))

(defn create-edit-connection-frame!
  "Create add-or-edit connection dialog.

  Parameters:

  - conn-list-frame - parent frame that contains list of defined connections,
  - conn-data - if not nil, then data of connection being edited,
  - drivers - list of JDBC driver class names to populate dropdown,
  - save! - save handler to be called when user commits edit,
  - test-conn! - function that tests conn.
  "
  [conn-list-frame conn-data drivers plugins save! test-conn!]
  {:pre [(or (nil? drivers)
             (and
               (coll? drivers)
               (not (empty? drivers))
               (every? sequential? drivers)))
         (sequential? plugins)]}
  (let [conn-name (:name conn-data)
        conn-jar (:jar conn-data)
        conn-class (:class conn-data)
        conn-str (:conn conn-data)
        conn-desc (:desc conn-data)
        label-texts ["Name"
                     "Driver JAR file (optional)"
                     "Driver Class"
                     ""
                     "JDBC Connection String"
                     "Description (optional)"]
        labels (map #(label :text % :border 2 :preferred-size [100 :by 0]) label-texts)
        fields [(text :id :name :text conn-name :preferred-size [600 :by 0])
                (text :id :jar :text conn-jar)
                (if drivers
                  (let [model (map first drivers)                          ; for now only classes
                        _combobox (combobox :id :class
                                            :editable? true
                                            :model model)]
                    (seesaw.core/selection! _combobox (or conn-class ""))
                    (listen _combobox :action (partial on-combo-action! plugins))
                    _combobox)
                  (text :id :class :text conn-class))
                (label :id :jdbc-conn-str-tmpl
                       :font (font :size 10)
                       :text (or (when conn-class (get-jdbc-url-template plugins conn-class))
                                 " "))
                (text :id :jdbc-conn-str :text conn-str)
                (text :id :desc :text conn-desc)]
        buttons [(button :id :cancel :text "Cancel" :listen [:action on-btn-save-connection-cancel])
                 (button :id :test :text "Test" :listen [:action (partial on-btn-test-connection test-conn!)])
                 (button :id :ok :text "Ok" :listen [:action (partial on-btn-save-connection-ok! save! conn-data)])]
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

