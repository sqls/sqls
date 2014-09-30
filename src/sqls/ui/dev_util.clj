
(ns sqls.ui.dev-util
  (:import (java.awt Container)))


(defn print-ui-tree
  ([^Container root]
   (assert (not= root nil))
   (print-ui-tree root 0))
  ([^Container root
    indent]
   (assert (not= root nil))
   (let [indent-str (apply str (repeat indent " "))]
     (println (str indent-str (.getClass root))))
   (let [children (.getComponents root)]
     (doseq [c children] (print-ui-tree c (+ 1 indent))))))

