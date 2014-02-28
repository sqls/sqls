(ns sqls.ui
  "Deal with UI"
  (:use seesaw.core)
  (:require [sqls [stor :as stor]])
  (:require [sqls.ui.worksheet :as sqls.ui.worksheet])
  )


(defn build-connection-list-item
  "Convert one connection to UI item struct."
  [conn]
  {:name (conn "name")})


(defn build-connection-list-table
  "Create a component that contains list of connections, bo be added to respective container"
  [connections]
  (println (format "building table for %d connections" (count (vals connections))))
  (let [t (table
    :id :conn-list-table
    :model [:columns [:name]
            :rows (map build-connection-list-item (vals connections))])]
    (println "table" t)
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
        conn-jdbc-conn-str (value (select root [:#jdbc-conn-str]))
        conn-desc (value (select root [:#desc]))
        new-connections (stor/add-connection! conn-name conn-jdbc-conn-str conn-desc)
        old-conn-list-table (select conn-list-frame [:#conn-list-table])
        _ (println "old-conn-list-table" old-conn-list-table)
        new-conn-list-table (build-connection-list-table new-connections)
        _ (println "new-conn-list-table" new-conn-list-table)
        ]
    (replace! (select conn-list-frame [:#panel]) old-conn-list-table new-conn-list-table)
    (pack! conn-list-frame)
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


(defn on-btn-delete-click
  [e]
  (alert "Delete")
)


(defn on-btn-connect-click
  "Handle button Connect click action: open worksheet window bound to selected connection."
  [e]
  (let [worksheet-frame (sqls.ui.worksheet/create-worksheet-frame)]
    (println "worksheet-frame:" worksheet-frame)
    (show! worksheet-frame)
    )
)


(defn build-login-ui
  "Create login window"
  [connections]
  (let [btn-add (button :id :btn-new :text "Add" :listen [:action on-btn-add-click])
        btn-delete (button :id :btn-delete :text "Delete" :listen [:action on-btn-delete-click])
        btn-connect (button :id :btn-connect :text "Connect" :listen [:action on-btn-connect-click])]
    (-> (frame :title "SQLS"
               :content (vertical-panel :id :panel
                                        :items [
                                                (build-connection-list-table connections)
                                                (horizontal-panel :items [
                                                                          btn-add
                                                                          btn-delete
                                                                          btn-connect])])
               :on-close :exit)
        pack!
        show!)
  )
)



