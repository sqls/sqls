(ns sqls.core
  (:gen-class)
  (:use seesaw.core)
  (:use sqls.ui)
  (:require [sqls.stor :as stor]))

(defn -main
  [& args]
  (invoke-later
    (let [settings (stor/load-settings!)
          _ (println "settings:" settings)
          connections (settings "connections")
          _ (println "connections:" connections)]
      (sqls.ui/build-login-ui connections))))
