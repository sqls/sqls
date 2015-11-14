(ns sqls.util
  "Various utilities."
  (:import java.io.File
           java.lang.System [clojure.lang Atom])
  (:require [clojure.string :as string]))


(defn any?
  [c]
  (some identity c))


(defn all?
  [c]
  (every? identity c))


(defn not-nil?
  [x]
  (not (nil? x)))


(defn assert-not-nil
  [x]
  (assert (not-nil? x)))


(defn str?
  [x]
  (instance? String x))


(defn str-or-nil?
  [x]
  (or (nil? x) (str? x)))


(defn atom?
  [a]
  (and (not-nil? a) (instance? Atom a)))


(defn path-to-absolute-path
  "Return absolute path."
  [^String p]
  (let [f (File. p)]
    (.getAbsolutePath f)))


(defn startswith
  "String .startsWith so we don't call Java directly."
  [^String s
   ^String p]
  (.startsWith s p))


(defn endswith
  "Strnig .endsWith so we don't call Java directly."
  [^String s
   ^String e]
  (.endsWith s e))


(defn path-join
  "Join arguments as path.
  Takes seq of path elements."
  [parts]
  {:pre [every? #(instance? File %) parts]}
  (let [sep File/separator]
    (string/join sep (map str parts))))


(defn get-absolute-path
  [^File f]
  (.getAbsolutePath f))


(defn list-dir
  "Return contents of directory as seq of strings.
  Takes string or file as argument.
  Returns seq absolute paths as strings.
  "
  [d]
  (let [^File df (if (instance? File d) d (File. (str d)))
        filenames (seq (.list df))
        join-df-and-filename (fn [^String f] (path-join [(.getAbsolutePath df) f]))
        absolute-filenames (map join-df-and-filename filenames)]
    absolute-filenames))


(defn find-driver-jars
  "Return a list of absolute paths to driver jar files."
  []
  (let [home (System/getProperty "user.home")
        home-sqls (path-join [home ".sqls"])
        home-sqls-lib (path-join [home ".sqls" "lib"])
        dirs ["." "lib" home-sqls home-sqls-lib]
        is-jar? (fn [s] (endswith s ".jar"))
        list-jars (fn [d] (filter is-jar? (list-dir d)))
        lists-of-jars (map list-jars dirs)
        jars (apply concat lists-of-jars)]
    jars))


(defn dir-exists?
  "Check if dir exists."
  [^String d]
  (let [df (File. d)]
    (and df (.exists df) (.isDirectory df))))


(defn file-exists?
  "Check if file named by f exists."
  [^String f]
  (let [ff (File. f)]
  (and f
       ff
       (.exists ff)
       (.isFile ff))))


(defn is-writable?
  "Check if path is writable."
  [^String f]
  (let [df (File. f)]
    (.canWrite df)))


(defn parent-dir
  "Get parent directory"
  [^String d]
  (let [df (File. d)]
    (.getParent df)))


(defn debugf
  [fmt & args]
  (println (apply format fmt args)))


(defn info
  [& args]
  (apply println args))


(defn infof
  [fmt & args]
  (println (apply format fmt args)))


(defn warnf
  [fmt & args]
  (println (apply format fmt args)))


(defn spy
  ([value]
   (println value)
   value)
  ([msg value]
   (println (format "%s: %s" msg value))
   value))

