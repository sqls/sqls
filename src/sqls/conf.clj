
(ns sqls.conf
  "Configuration stuff, uses stor."
  (:import [java.io File])
  (:require [sqls.util :as util]))

(defn is-good-existing-conf-dir?
  "Check if dir looks like a correct existing conf-dir.
  Good is when:

  - dir exists,
  - it's writable,
  - contains \"settings.json\" file."
  [^String d]
  (println (format "checking if %s is good conf dir" d))
  (and
    (util/dir-exists? d)
    (util/is-writable? d)
    (util/file-exists? (util/path-join [d "settings.json"]))))

(defn is-good-potential-conf-dir?
  "Check if dir could be good conf dir even if it does not exist."
  [^String d]
  (let [parent (if d (util/parent-dir d))]
    (and parent
         (util/dir-exists? parent)
         (util/is-writable? parent)) parent))

(defn find-conf-dir
  "Find conf dir:

  - first look at given cwd (presumably working directory),
  - then look at $HOME/.config/sqls,
  - then look at $HOME/.sqls,
  - then look in OS specific user's configuration dir:

    - for OS X it's $HOME/Library/Application Support,
    - for Windows it's $HOME\\AppData\\Local in user's home.

  The idea is that user can store conf files in the same dir as app to have
  portable installation, or can manually store conf files in one of
  commonly used directories, but we'll fallback to given operating system's
  convention at the end.

  This fn does not create any files, and it takes \"current working directory\"
  as parameter."
  [^String cwd]
  (let [home (System/getProperty "user.home")
        home-dot-sqls (util/path-join [home ".sqls"])
        home-dot-conf-sqls (util/path-join [home ".config" "sqls"])
        os-x-lib (util/path-join [home "Library" "Application Support"])
        os-x-lib-app-support-sqls (util/path-join [os-x-lib "sqls"])
        win-appdata (System/getenv "APPDATA")
        win-appdata-sqls (if win-appdata (util/path-join [win-appdata "sqls"]))
        candidates [cwd home-dot-conf-sqls home-dot-sqls]
        good-existing (-> (filter is-good-existing-conf-dir? candidates) first)]
    (let [d (cond
              good-existing good-existing
              (is-good-potential-conf-dir? win-appdata-sqls) win-appdata-sqls
              (is-good-potential-conf-dir? os-x-lib-app-support-sqls) os-x-lib-app-support-sqls)]
      (println (format "found conf dir: %s" d))
      d)))

(defn ensure-conf-dir!
  "Make sure conf directory exists and is writable."
  [^String d]
  (if-not (and (util/dir-exists? d) (util/is-writable? d))
    (.mkdirs (File. d)))
  (let [settings-json-path (util/path-join [d "settings.json"])]
    (if-not (util/file-exists? settings-json-path)
      (spit settings-json-path "{}"))))

