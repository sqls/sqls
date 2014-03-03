
(ns sqls.ui
  "Deal with UI"
  (:use seesaw.core)
  (:require [seesaw.bind :as b])
  (:require [sqls.stor :as stor])
  (:require seesaw.table)
  (:require sqls.ui.worksheet))


(defn build-connection-list-item
  "Convert one connection to UI item struct."
  [conn]
  {:name (conn "name")
   :desc (conn "desc")
   :class (conn "class")
   :conn-data conn})


(defn build-connection-list-table
  "Create a UI component that contains list of connections, bo be added to respective container.
  Parameter connections contains a list of conn-data maps, this parameter can be nil."
  [connections]
  (let [t (table
            :id :conn-list-table
            :preferred-size [480 :by 320]
            :model [:columns [:name :desc :class]
                    :rows (map build-connection-list-item connections)])]
    t))


(defn on-btn-add-connection-cancel
  "Just close the dialog"
  [e]
  (dispose! e))


(defn on-btn-add-connection-click
  "Handle add connection click.
  
  This is intended to be run through partial with parent frame (the one that contains conn list).
  
  1. Store new connection data by calling stor/new-connection.
  2. Replace connections list in parent frame.
  3. Close add connection dialog."
  [conn-list-frame e]
  (println "conn-list-frame" conn-list-frame)
  (let [
        root (to-root e)
        conn-name (value (select root [:#name]))
        conn-class (value (select root [:#class]))
        conn-jdbc-conn-str (value (select root [:#jdbc-conn-str]))
        conn-desc (value (select root [:#desc]))
        conn-data {"name" conn-name
                   "class" conn-class
                   "jdbc-conn-str" conn-jdbc-conn-str
                   "desc" conn-desc}
        new-connections (stor/add-connection! conn-data)
        old-conn-list-table (select conn-list-frame [:#conn-list-table])
        _ (println "old-conn-list-table" old-conn-list-table)
        new-conn-list-table (build-connection-list-table new-connections)
        _ (println "new-conn-list-table" new-conn-list-table)
        ]
    (replace! (.getParent old-conn-list-table) old-conn-list-table new-conn-list-table)
    ; (pack! conn-list-frame)
    (dispose! e)
    )
  )


(defn build-add-connection-ui
  "Create add connection window"
  [conn-list-frame]
  (-> (custom-dialog :title "SQLS: Add connection"
             :content (vertical-panel
                :items [
                  (grid-panel
                    :columns 2
                    :hgap 10
                    :vgap 10
                    :items [
                            "Name" (text :id :name)
                            "Driver Class" (text :id :class)
                            "JDBC Connection String" (text :id :jdbc-conn-str)
                            "Description" (text :id :desc)])
                  (horizontal-panel
                    :items [
                      (button :id :cancel :text "Cancel" :listen [:action on-btn-add-connection-cancel])
                      (button :id :ok :text "Ok" :listen [:action (partial on-btn-add-connection-click conn-list-frame)])
                    ]
                  )
                ]
             )
      )
      pack!
  )
)


(defn on-btn-add-click
  [e]
  (let [
        add-connection-win (build-add-connection-ui (to-root e))]
    (show! add-connection-win)
  )
)


(defn get-selected-conn-data
  "Extract currently selected conn-data from connection list frame."
  [frame]
  (let [table (select frame [:#conn-list-table])
        _ (println "conn list table: " table)
        selected-table-item (selection table)
        _ (println "selected table item:" selected-table-item ", type:" (type selected-table-item))
        conn-item (seesaw.table/value-at table selected-table-item)
        conn-data (:conn-data conn-item)
        _ (println "conn-data:" conn-data)]
    conn-data))


(defn on-btn-delete-click
  "User clicks delete button in connection list window.
  TODO: ask for confirmation asynchronously (but modally)."
  [e]
  (let [frame (to-root e)
        conn-data (get-selected-conn-data frame)]
    (if (not= conn-data nil)
      (let [name (conn-data "name")
            new-connections (stor/delete-connection! name)
            old-conn-list-table (select frame [:#conn-list-table])
            new-conn-list-table (build-connection-list-table new-connections)]
        (replace! (.getParent old-conn-list-table) old-conn-list-table new-conn-list-table)))))


(defn on-btn-connect-click
  "Handle button Connect click action: open worksheet window bound to selected connection.
  Returns worksheet data structure that contains conn-data and frame.
  
  Parameters:
  
  - conn-list-frame - parent frame,
  - create-worksheet! - function that sets up and creates worksheet (passed to create-login-ui).
  "
  [conn-list-frame create-worksheet! e]
  (println "connect btn clicked: frame:" conn-list-frame ", event:" e)
  (let [conn-data (get-selected-conn-data conn-list-frame)]
    (println "conn-data:" conn-data)
    (create-worksheet! conn-data)))


(defn set-conn-list-frame-bindings!
  [frame]
  (let [conn-list-table (select frame [:#conn-list-table])
        btn-connect (select frame [:#btn-connect])
        btn-delete (select frame [:#btn-delete])]
    (assert (not= conn-list-table nil))
    (assert (not= btn-connect nil))
    (assert (not= btn-delete nil))
    (b/bind
      (b/selection conn-list-table)
      (b/transform (fn [s] (not= s nil)))
      (b/property btn-connect :enabled?)
      (b/property btn-delete :enabled?))))  


(defn create-login-ui
  "Create login window.
  Parameters:

  - handlers - maps of handlers with following keys:
  
    - create-worksheet - called when user clicks connect button,
    
  - connections - list of connections do display."
  [handlers settings connections]
  (println "settings" settings)
  (println "connections" connections)
  (let [btn-add (button :id :btn-new :text "Add" :listen [:action on-btn-add-click])
        btn-delete (button :id :btn-delete :text "Delete" :enabled? false :listen [:action on-btn-delete-click])
        btn-connect (button :id :btn-connect :text "Connect" :enabled? false)
        frame (frame :title "SQLS"
               :content (vertical-panel :id :panel
                                        :border 4
                                        :items [
                                                (scrollable (build-connection-list-table connections))
                                                (horizontal-panel :border 4
                                                                  :items [
                                                                          btn-add
                                                                          btn-delete
                                                                          btn-connect])])
               :on-close :exit)]
    (listen btn-connect :action (partial on-btn-connect-click frame (:create-worksheet handlers)))
    (set-conn-list-frame-bindings! frame)
    frame))

