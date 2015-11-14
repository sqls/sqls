(ns sqls.model
  (:gen-class))

(defrecord Conn [name
                 desc
                 class
                 jar
                 conn])

(defn ->conn
  [^String name
   ^String desc
   ^String class
   ^String jar
   ^String conn]
  {:pre [(not (nil? name))
         (not (nil? class))
         (not (nil? conn))]}
  (->Conn name desc class jar conn))

(defn conn?
  [c]
  (instance? Conn c))

