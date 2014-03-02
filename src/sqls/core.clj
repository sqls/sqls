(ns sqls.core
  (:gen-class)
  (:use seesaw.core)
  (:use sqls.ui)
  (:require [sqls.stor :as stor])
  (:require sqls.worksheet))

(defn -main
  [& args]
  (invoke-later
    (let [settings (stor/load-settings!)
          connections (stor/load-connections!)
          handlers {:create-worksheet sqls.worksheet/create-and-show-worksheet!}
          conn-list-frame (sqls.ui/create-login-ui handlers settings connections)]
      (pack! conn-list-frame)
      (show! conn-list-frame))))
