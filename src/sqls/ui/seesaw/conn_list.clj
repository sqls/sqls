(ns sqls.ui.seesaw.conn-list
  "Conn list window UI implementation."
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.string :as s]
    fipp.edn
    seesaw.core
    [seesaw.bind :as b]
    seesaw.keymap
    seesaw.keystroke
    seesaw.table
    [sqls.model :refer [conn?]]
    [sqls.util :refer [all? atom? infof is-uniq-elems? spy]]
    [sqls.ui.proto :refer [ConnListWindow show-conn-list-window!]]
    sqls.ui.seesaw.conn-edit)
  (:import
    [clojure.lang Atom]
    [java.awt Point]
    [java.awt.event MouseEvent]
    [javax.swing JFrame JTable]
    [javax.swing.table AbstractTableModel]))


(defn build-connection-list-table!
  "Create a UI component that contains list of connections, bo be added to respective container.
  Parameter conns is an atom that contains a list of conn-data maps, this parameter can be nil.
  Returns map of table and model."
  [enabled-connections-atom conns-atom]
  {:pre [(or (nil? conns-atom) (and (atom? conns-atom) (vector? @conns-atom)))]}
  (let [build-model! (fn [enabled-connections-atom conns]
                       (let [column-names [""
                                           "name"
                                           "desc"
                                           "class"
                                           "jar"]
                             column-index-to-key-map {0 :connected
                                                      1 :name
                                                      2 :desc
                                                      3 :class
                                                      4 :jar}]
                         (proxy
                           [AbstractTableModel]
                           []
                           (getRowCount [] (count @conns-atom))
                           (getColumnCount [] 5)
                           (getColumnName [column]
                             (let [column-name (get column-names column)]
                               (assert (not (nil? column-name)))
                               column-name))
                           (getValueAt [row column]
                             (if (= column -1)
                               (get @conns-atom row)
                               (if (or (= row -1) (= column -1))
                                 nil
                                 (let [column-key (get column-index-to-key-map column)]
                                   (assert (not (nil? column-key)))
                                   (let [raw-value (if (= :connected column-key)
                                                     (not (contains? @enabled-connections-atom (-> conns deref (get row) (get :name))))
                                                     (-> conns
                                                         deref
                                                         (get row)
                                                         (get column-key)))
                                         value (if (= column-key :connected)
                                                 (if raw-value
                                                   " "
                                                   "+")
                                                 raw-value)]
                                     value))))))))
        model (build-model! enabled-connections-atom conns-atom)
        table (seesaw.core/table
                :id :conn-list-table
                :preferred-size [1024 :by 768]
                :model model)]
    (let [column-model (.getColumnModel table)
          column (.getColumn column-model 0)
          w 25]
      (.setPreferredWidth column w)
      (.setMaxWidth column w)
      (.setMinWidth column w)
      (.setWidth column w)
      (.setResizable column false))
    {:model model :table table}))

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
    (let [is-connectable? (fn [^String conn-name]
                            (assert (not (nil? conn-name)))
                            (let [x (contains? @enabled-connections-atom conn-name)]
                              (infof "is-connectable? conn-name: %s -> %s" conn-name x)
                              x))
          src-on-list-chain (b/bind
                              (b/selection conn-list-table)
                              (b/transform (fn [sel]
                                             (infof "src-on-list-chain: conn-list-table selection is now %s" sel)
                                             (if sel
                                               (->> sel
                                                    (seesaw.table/value-at conn-list-table)
                                                    ((fn [val]
                                                       (infof "src-on-list: value: %s" val)
                                                       val
                                                       ))
                                                    :name
                                                    is-connectable?)
                                               false))))
          src-on-atom-chain (b/bind
                              enabled-connections-atom
                              (b/transform (fn [_]
                                             (let [sel (->> (seesaw.core/selection conn-list-table)
                                                            (seesaw.table/value-at conn-list-table))]
                                               (if sel
                                                 (-> sel
                                                  :name
                                                  is-connectable?)
                                                 false)))))]
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
        selected-table-item (seesaw.core/selection table)]
    (seesaw.table/value-at table selected-table-item)))

(defn get-selected-conn-name
  [^JFrame frame]
  (assert (not= frame nil))
  (:name (spy (get-selected-conn-data frame))))

(defn on-btn-add-click!
  "Handle add connection button in conn list frame."
  [drivers plugins save! test-conn! e]
  (let [add-connection-frame (sqls.ui.seesaw.conn-edit/create-edit-connection-frame!
                               (seesaw.core/to-root e)
                               nil
                               drivers
                               plugins
                               save!
                               test-conn!)]
    (seesaw.core/show! add-connection-frame)))

(defn on-btn-edit-click!
  "Edit button was clicked."
  [conn-list-frame drivers plugins save! test-conn! _e]
  (let [conn-data (get-selected-conn-data conn-list-frame)
        edit-connection-frame (sqls.ui.seesaw.conn-edit/create-edit-connection-frame!
                                conn-list-frame
                                conn-data
                                drivers
                                plugins
                                save!
                                test-conn!)]
    (seesaw.core/show! edit-connection-frame)))

(defn on-btn-delete-click!
  "User clicks delete button in connection list window.
  Params:
  - delete-connection! - the handler to actually delete connection from the parent,
  - conns-atom - the state of the conn-list component, a vector of conns
  - conns-table-model - the model of the table
  - e - the UI event, ignored"
  [delete-connection! conns-atom conn-list-table-model e]
  (assert (not= e nil))
  (assert (not= delete-connection! nil))
  (assert (ifn? delete-connection!))
  (let [frame (seesaw.core/to-root e)
        conn-data (get-selected-conn-data frame)]
    (when-not (= conn-data nil)
      (let [conn-name (:name conn-data)
            new-connections (delete-connection! conn-name)]
        (swap!
          conns-atom
          (fn [conns]
            (vec
              (filter
                (fn [conn] (not (= conn-name (:name conn))))
                conns))))
        (.fireTableDataChanged conn-list-table-model)))))

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
  (let [conn-name (get-selected-conn-name conn-list-frame)]
    (assert conn-name)
    (assert (string? conn-name))
    (assert (not (empty? (s/trim conn-name))))
    (create-worksheet! conn-list-window conn-name)))

(defn enable-conn!
  [enabled-conns-atom conn-list-table-model conn-name]
  {:pre [(string? conn-name)]}
  (swap! enabled-conns-atom conj conn-name)
  (.fireTableDataChanged conn-list-table-model))

(defn disable-conn!
  [enabled-conns-atom conn-list-table-model conn-name]
  {:pre [string? conn-name]}
  ; I don't like the enabled-conns-atom...
  (swap! enabled-conns-atom disj conn-name)
  (infof "disable-conn!: the enabled-conns-atom is now %s" @enabled-conns-atom)
  (.fireTableDataChanged conn-list-table-model))

(defn on-conn-list-table-mouse-clicked!
  [conn-list-window
   create-worksheet!
   ^MouseEvent event]
  {:pre [(ifn? create-worksheet!)]}
  (let [click-count (.getClickCount event)]
    (when (= 2 click-count)
      (let [^JTable table (.getSource event)
            ^Point point (.getPoint event)
            row-index (.rowAtPoint table point)]
        (when (or (zero? row-index) (pos? row-index))
          (let [conn-name (:name (seesaw.table/value-at table row-index))]
            (assert (not (nil? conn-name)))
            (assert (string? conn-name))
            (create-worksheet! conn-list-window conn-name)))))))

(defn create-conn-list-window!
  "Create login window.
  Parameters:

  - ui - the UI,
  - about-text - text in 'About' popup,
  - drivers - seq of possible driver class names,
  - plugins - sequence o plugins,
  - handlers - maps of handlers with following keys:

    - create-worksheet - called when user clicks connect button,
    - save-conn - called when user clicks save/add in edit/add connection dialog,
    - conn-list-closed - called after conn-list window is closed,

  - connections - list of connections do display."
  [ui
   about-text
   drivers
   plugins
   handlers
   connections]
  {:pre [(not (nil? ui))
         (sequential? plugins)
         (map? handlers)
         (:create-worksheet handlers)
         (:conn-list-closed handlers)]
   :post [(not (nil? %))]}
  (let [enabled-conns-atom (atom (apply hash-set (map :name connections)))
        conns-atom (atom (vec connections))
        delete-connection! (:delete-connection! handlers)
        btn-about (seesaw.core/button :id :btn-about :text "About" :listen [:action (fn [_] (sqls.ui.proto/show-about! ui about-text))])
        btn-add (seesaw.core/button :id :btn-new :text "Add")
        btn-edit (seesaw.core/button :id :btn-edit :text "Edit" :enabled? false)
        btn-delete (seesaw.core/button :id :btn-delete
                                       :text "Delete"
                                       :enabled? false
                                       :listen [:action (partial on-btn-delete-click! delete-connection!)])
        btn-connect (seesaw.core/button :id :btn-connect :text "Connect" :enabled? false)
        {conn-list-table :table
         conn-list-table-model :model} (build-connection-list-table! enabled-conns-atom conns-atom)
        _ (assert (not (nil? conn-list-table)))
        _ (assert (not (nil? conn-list-table-model)))
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
    (seesaw.core/listen frame :window-closed (fn [_] ((:conn-list-closed handlers))))
    (seesaw.keymap/map-key frame
                           (seesaw.keystroke/keystroke "menu W")
                           (fn [_] (seesaw.core/dispose! frame))
                           :scope :global)
    (let [conn-list-window (reify ConnListWindow
                             (show-conn-list-window!
                               [_]
                               (seesaw.core/pack! frame)
                               (seesaw.core/show! frame))
                             (enable-conn! [_ conn-name] (enable-conn! enabled-conns-atom conn-list-table-model conn-name))
                             (disable-conn! [_ conn-name] (disable-conn! enabled-conns-atom conn-list-table-model conn-name))
                             (add-conn! [_ conn]
                               (swap!
                                 conns-atom
                                 (fn [conns] conns))))]
      (seesaw.core/listen conn-list-table :mouse-clicked (partial on-conn-list-table-mouse-clicked! conn-list-window (:create-worksheet handlers)))
      ;; We should not pass plugins here… instead UI should be dumb… we should probably have a handler
      ;; to get JDBC template for given params.
      (seesaw.core/listen btn-add :action (partial on-btn-add-click! drivers plugins (:save-conn handlers) (:test-conn handlers)))
      (seesaw.core/listen btn-edit :action (partial on-btn-edit-click! frame drivers plugins (:save-conn handlers) (:test-conn handlers)))
      (seesaw.core/listen btn-connect :action (partial on-btn-connect-click! frame conn-list-window (:create-worksheet handlers)))
      (set-conn-list-frame-bindings! frame enabled-conns-atom)
      conn-list-window)))

