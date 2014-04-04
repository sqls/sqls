

(ns sqls.util
  "Various utilities."
  (:import java.io.File)
  (:import java.lang.System)
  )


(defn path-to-absolute-path
  "Return absolute path."
  [^String p]
  (let [f (java.io.File. p)]
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
  "Join arguments as path. This should be expressed as fold.
  Takes seq of path elements, returns them joined as absolute path."
  [parts]
  (let [^String base (first parts)
        others (rest parts)]
    (if (empty? others)
      base
      (let [^String first-other (first others)
            ^String new-base (.getAbsolutePath (java.io.File. base first-other))
            new-others (rest others)]
        (path-join (cons new-base new-others))))))


(defn get-absolute-path
  [^java.io.File f]
  (.getAbsolutePath f))


(defn list-dir
  "Return contents of directory as seq of strings.
  Takes string or file as argument.
  Returns seq absolute paths as strings.
  "
  [d]
  (let [^java.io.File df (if (instance? java.io.File d) d (java.io.File. d))
        filenames (seq (.list df))
        join-df-and-filename (fn [f] (path-join [df f]))
        absolute-filenames (map join-df-and-filename filenames)]
    absolute-filenames))


(defn find-driver-jars
  "Return a list of absolute paths to driver jar files."
  []
  (let [home (java.lang.System/getProperty "user.home")
        _ (println "home:" home)
        home-sqls (path-join [home ".sqls"])
        home-sqls-lib (path-join [home ".sqls" "lib"])
        dirs ["." "lib" home-sqls home-sqls-lib]
        _ (println "dirs to search for jar files:" dirs)
        is-jar? (fn [s] (endswith s ".jar"))
        list-jars (fn [d] (filter is-jar? (list-dir d)))
        lists-of-jars (map list-jars dirs)
        jars (apply concat lists-of-jars)]
    (println "jar files:" jars)
    jars))
