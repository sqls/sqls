
(ns sqls.ui.conn-list
  "Conn list window UI"
  (:import [javax.swing JFrame]
           [clojure.lang Atom])
  (:require
    seesaw.core
    seesaw.table
    [seesaw.bind :as b])
  (:require [sqls.util :refer [all? any?]]
            sqls.ui.conn-edit))


(defn build-connection-list-item
  "Convert one connection to UI item struct."
  [conn]
  (assert (not= conn nil))
  {:name (conn "name")
   :desc (conn "desc")
   :class (conn "class")
   :jar (conn "jar")
   :conn-data conn})


(defn build-connection-list-model
  "Return model for UI table based on connection list."
  [connections]
  [:columns [:name :desc :class :jar]
   :rows (map build-connection-list-item connections)])


(defn build-connection-list-table
  "Create a UI component that contains list of connections, bo be added to respective container.
  Parameter connections contains a list of conn-data maps, this parameter can be nil."
  [connections]
  (let [t (seesaw.core/table
            :id :conn-list-table
            :preferred-size [480 :by 320]
            :model (build-connection-list-model connections))]
    t))


(defn set-conn-list-frame-bindings!
  [^JFrame frame
   ^Atom sqls-atom
   is-connectable?]
  (let [conn-list-table (seesaw.core/select frame [:#conn-list-table])
        btn-edit (seesaw.core/select frame [:#btn-edit])
        btn-connect (seesaw.core/select frame [:#btn-connect])
        btn-delete (seesaw.core/select frame [:#btn-delete])]
    (assert (not= conn-list-table nil))
    (assert (not= btn-connect nil))
    (assert (not= btn-delete nil))
    (assert (not= btn-edit nil))
    (let [src-on-list-chain (b/bind
                              (b/selection conn-list-table)
                              (b/transform (fn
                                             [sel]
                                             (if (not= nil sel)
                                               (let [val (seesaw.table/value-at conn-list-table sel)
                                                     name (:name val)]
                                                 (assert name)
                                                 (is-connectable? name))
                                               false))))
          src-on-atom-chain (b/bind
                              sqls-atom
                              (b/transform (fn
                                             [v]
                                             (let [worksheets (:worksheets v)
                                                   _ (assert (not= nil worksheets))
                                                   sel (seesaw.core/selection conn-list-table)
                                                   val (seesaw.table/value-at conn-list-table sel)
                                                   name (:name val)
                                                   _ (assert (not= name nil))
                                                   r (is-connectable? name)
                                                   _ (println (format "is connectable %s? -> %s" name r))]
                                               r))))]
      (doseq [src [src-on-list-chain src-on-atom-chain]]
        (b/bind
          src
          (b/property btn-connect :enabled?)
          (b/property btn-delete :enabled?)
          (b/property btn-edit :enabled?))))))


(defn get-selected-conn-data
  "Extract currently selected conn-data from connection list frame."
  [^JFrame frame]
  (assert (not= frame nil))
  (let [table (seesaw.core/select frame [:#conn-list-table])
        selected-table-item (seesaw.core/selection table)
        conn-item (seesaw.table/value-at table selected-table-item)
        conn-data (:conn-data conn-item)]
    conn-data))


(defn on-btn-add-click
  "Handle add connection button in conn list frame."
  [save! test-conn! e]
  (let [add-connection-frame (sqls.ui.conn-edit/create-edit-connection-frame
                               (seesaw.core/to-root e)
                               nil
                               save!
                               test-conn!)]
    (seesaw.core/show! add-connection-frame)
    )
  )


(defn on-btn-edit-click
  "Edit button was clicked."
  [conn-list-frame save! test-conn! _e]
  (let [conn-data (get-selected-conn-data conn-list-frame)
        edit-connection-frame (sqls.ui.conn-edit/create-edit-connection-frame conn-list-frame conn-data save! test-conn!)]
    (seesaw.core/show! edit-connection-frame)))


(defn on-btn-delete-click
  "User clicks delete button in connection list window.
  TODO: ask for confirmation asynchronously (but modally)."
  [delete-connection! e]
  (println (str "btn delete click: " e))
  (assert (not= e nil))
  (assert (not= delete-connection! nil))
  (let [frame (seesaw.core/to-root e)
        conn-data (get-selected-conn-data frame)]
    (if (not= conn-data nil)
      (let [name (conn-data "name")
            new-connections (delete-connection! name)
            conn-list-table (seesaw.core/select frame [:#conn-list-table])]
        (seesaw.core/config! conn-list-table :model (build-connection-list-model new-connections))))))


(defn on-btn-connect-click
  "Handle button Connect click action: open worksheet window bound to selected connection.
  Returns worksheet data structure that contains conn-data and frame.

  Parameters:

  - conn-list-frame - parent frame,
  - create-worksheet! - function that sets up and creates worksheet (passed to create-login-ui).
  "
  [conn-list-frame create-worksheet! _]
  (let [conn-data (get-selected-conn-data conn-list-frame)]
    (create-worksheet! conn-data)))


(defn create-login-ui
  "Create login window.
  Parameters:

  - handlers - maps of handlers with following keys:

    - create-worksheet - called when user clicks connect button,
    - save-conn - called when user clicks save/add in edit/add connection dialog,

  - connections - list of connections do display."
  [sqls-atom exit-on-close? handlers _settings connections]
  (let [delete-connection! (:delete-connection! handlers)
        is-connectable? (:is-connectable? handlers)
        btn-about (seesaw.core/button :id :btn-about :text "About")
        btn-add (seesaw.core/button :id :btn-new :text "Add")
        btn-edit (seesaw.core/button :id :btn-edit :text "Edit" :enabled? false)
        btn-delete (seesaw.core/button :id :btn-delete
                                       :text "Delete"
                                       :enabled? false
                                       :listen [:action (partial on-btn-delete-click delete-connection!)])
        btn-connect (seesaw.core/button :id :btn-connect :text "Connect" :enabled? false)
        frame (seesaw.core/frame
                :title "SQLS"
                :content (seesaw.core/vertical-panel
                           :id :panel
                           :border 4
                           :items [(seesaw.core/scrollable (build-connection-list-table connections))
                                   (seesaw.core/horizontal-panel :border 4
                                                                 :items [btn-about
                                                                         btn-add
                                                                         btn-edit
                                                                         btn-delete
                                                                         btn-connect])])
                :on-close (if exit-on-close? :exit :dispose))]
    (assert (not= nil is-connectable?))
    (seesaw.core/listen btn-about :action (fn [_] ((:about handlers))))
    (seesaw.core/listen btn-add :action (partial on-btn-add-click (:save-conn handlers) (:test-conn handlers)))
    (seesaw.core/listen btn-edit :action (partial on-btn-edit-click frame (:save-conn handlers) (:test-conn handlers)))
    (seesaw.core/listen btn-connect :action (partial on-btn-connect-click frame (:create-worksheet handlers)))
    (set-conn-list-frame-bindings! frame sqls-atom is-connectable?)
    frame))
