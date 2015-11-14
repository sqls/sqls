(ns sqls.ui.seesaw.conn-list
  "Conn list window UI implementation."
  (:require
    [clojure.pprint :refer [pprint]]
    seesaw.core
    [seesaw.bind :as b]
    seesaw.table
    [sqls.model :refer [conn?]]
    [sqls.util :refer [all? any?]]
    [sqls.ui.proto :refer [ConnListWindow show-conn-list-window!]]
    sqls.ui.seesaw.conn-edit)
  (:import
    [clojure.lang Atom]
    [javax.swing JFrame]))


(defn build-connection-list-item
  "Convert one connection to UI item struct."
  [conn]
  (assert (not= conn nil))
  {:name (:name conn)
   :desc (:desc conn)
   :class (:class conn)
   :jar (:jar conn)
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
   ^Atom enabled-connections-atom]
  (let [conn-list-table (seesaw.core/select frame [:#conn-list-table])
        btn-edit (seesaw.core/select frame [:#btn-edit])
        btn-connect (seesaw.core/select frame [:#btn-connect])
        btn-delete (seesaw.core/select frame [:#btn-delete])]
    (assert (not= conn-list-table nil))
    (assert (not= btn-connect nil))
    (assert (not= btn-delete nil))
    (assert (not= btn-edit nil))
    (let [is-connectable? (fn [^String conn-name] (contains? @enabled-connections-atom conn-name))
          src-on-list-chain (b/bind
                              (b/selection conn-list-table)
                              (b/transform (fn [sel]
                                             (if (not= nil sel)
                                               (->> sel
                                                    (seesaw.table/value-at conn-list-table)
                                                    :name
                                                    is-connectable?)
                                               false))))
          src-on-atom-chain (b/bind
                              enabled-connections-atom
                              (b/transform (fn [_]
                                             (->> (seesaw.core/selection conn-list-table)
                                                  (seesaw.table/value-at conn-list-table)
                                                  :name
                                                  is-connectable?))))]
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


(defn on-btn-add-click!
  "Handle add connection button in conn list frame."
  [drivers save! test-conn! e]
  (let [add-connection-frame (sqls.ui.seesaw.conn-edit/create-edit-connection-frame!
                               (seesaw.core/to-root e)
                               nil
                               drivers
                               save!
                               test-conn!)]
    (seesaw.core/show! add-connection-frame)))

(defn on-btn-edit-click!
  "Edit button was clicked."
  [conn-list-frame drivers save! test-conn! _e]
  (let [conn-data (get-selected-conn-data conn-list-frame)
        edit-connection-frame (sqls.ui.seesaw.conn-edit/create-edit-connection-frame!
                                conn-list-frame
                                conn-data
                                drivers
                                save!
                                test-conn!)]
    (seesaw.core/show! edit-connection-frame)))

(defn on-btn-delete-click!
  "User clicks delete button in connection list window."
  [delete-connection! e]
  (assert (not= e nil))
  (assert (not= delete-connection! nil))
  (assert (ifn? delete-connection!))
  (let [frame (seesaw.core/to-root e)
        conn-data (get-selected-conn-data frame)]
    (when-not (= conn-data nil)
      (let [name (:name conn-data)
            new-connections (delete-connection! name)
            conn-list-table (seesaw.core/select frame [:#conn-list-table])]
        (seesaw.core/config! conn-list-table :model (build-connection-list-model new-connections))))))

(defn on-btn-connect-click!
  "Handle button Connect click action: open worksheet window bound to selected connection.
  Returns worksheet data structure that contains conn-data and frame.

  Parameters:

  - conn-list-frame - parent frame,
  - conn-list-window - the object that encapsulates frame (reify ConnListWindow),
  - create-worksheet! - function that sets up and creates worksheet (passed to create-login-ui),
  - _event - UI event.
  "
  [conn-list-frame conn-list-window create-worksheet! _event]
  (let [conn-data (get-selected-conn-data conn-list-frame)]
    (assert conn-data)
    (create-worksheet! conn-list-window (:name conn-data))))

(defn enable-conn!
  [conns-atom conn-name]
  {:pre [string? conn-name]}
  (println (format "enable-conn %s" conn-name))
  (swap! conns-atom conj conn-name))

(defn disable-conn!
  [conns-atom conn-name]
  {:pre [string? conn-name]}
  (swap! conns-atom disj conn-name))

(defn set-conns!
  [table conns]
  {:pre [(sequential? conns)
         (every? conn? conns)
         (every? :name conns)]}
  (let [rows (map build-connection-list-item conns)]
    (seesaw.table/clear! table)
    (when-not (empty? rows)
      (doseq [[i row] (map vector (range) rows)]
        (seesaw.table/insert-at! table i row)))))

(defn create-conn-list-window!
  "Create login window.
  Parameters:

  - drivers - seq of possible driver class names,
  - handlers - maps of handlers with following keys:

    - create-worksheet - called when user clicks connect button,
    - save-conn - called when user clicks save/add in edit/add connection dialog,
    - conn-list-closed - called after conn-list window is closed,

  - connections - list of connections do display."
  [ui
   about-text
   drivers
   handlers
   connections]
  {:pre [(not (nil? ui))
         (map? handlers)
         (:create-worksheet handlers)
         (:conn-list-closed handlers)]
   :post [(not (nil? %))]}
  (let [enabled-connections-atom (atom (apply hash-set (map :name connections)))
        delete-connection! (:delete-connection! handlers)
        btn-about (seesaw.core/button :id :btn-about :text "About" :listen [:action (fn [_] (sqls.ui.proto/show-about! ui about-text))])
        btn-add (seesaw.core/button :id :btn-new :text "Add")
        btn-edit (seesaw.core/button :id :btn-edit :text "Edit" :enabled? false)
        btn-delete (seesaw.core/button :id :btn-delete
                                       :text "Delete"
                                       :enabled? false
                                       :listen [:action (partial on-btn-delete-click! delete-connection!)])
        btn-connect (seesaw.core/button :id :btn-connect :text "Connect" :enabled? false)
        conn-list-table (build-connection-list-table connections)
        _ (assert (not (nil? conn-list-table)))
        frame (seesaw.core/frame
                :title "SQLS"
                :on-close :dispose
                :content (seesaw.core/vertical-panel
                           :id :panel
                           :border 4
                           :items [(seesaw.core/scrollable conn-list-table)
                                   (seesaw.core/horizontal-panel :border 4
                                                                 :items [btn-about
                                                                         btn-add
                                                                         btn-edit
                                                                         btn-delete
                                                                         btn-connect])]))]
    (seesaw.core/listen frame :window-closed (fn [_]
                                               (println "window closed")
                                               ((:conn-list-closed handlers))))
    (let [conn-list-window (reify
                             ConnListWindow
                             (show-conn-list-window!
                               [_]
                               (seesaw.core/pack! frame)
                               (seesaw.core/show! frame))
                             (enable-conn! [_ conn-name] (enable-conn! enabled-connections-atom conn-name))
                             (disable-conn! [_ conn-name] (disable-conn! enabled-connections-atom conn-name))
                             (set-conns! [_ conns] (set-conns! conn-list-table conns)))]
      (seesaw.core/listen btn-add :action (partial on-btn-add-click! drivers (:save-conn handlers) (:test-conn handlers)))
      (seesaw.core/listen btn-edit :action (partial on-btn-edit-click! frame drivers (:save-conn handlers) (:test-conn handlers)))
      (seesaw.core/listen btn-connect :action (partial on-btn-connect-click! frame conn-list-window (:create-worksheet handlers)))
      (set-conn-list-frame-bindings! frame enabled-connections-atom)
      conn-list-window)))

