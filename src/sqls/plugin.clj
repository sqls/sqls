(ns sqls.plugin
  (:require [taoensso.timbre :refer [debugf]]))

(defn plugin-for-class?
  "Check if plugin supports given JDBC driver class."
  [class-name plugin]
  {:pre [(string? class-name)
         (map? plugin)
         (let [classes-fn (:classes plugin)]
           (or (nil? classes-fn)
               (ifn? classes-fn)))]}
  (let [plugin-classes (into #{} (map first ((:classes plugin))))]
    (contains? plugin-classes class-name)))

(defn get-plugins-by-class
  "Find plugins by JDBC driver class name.
  Note that each plugin can support many classes.
  Params:
  - class-name - connection class name string,
  - plugins - some collection of plugins, each defined as map."
  [class-name plugins]
  {:pre [(string? class-name)
         (sequential? plugins)]}
  (filter (partial plugin-for-class? class-name) plugins))

(defn describe-object!
  "Get object description from DB.
  Note that each describe is maybe heavy (DB call).
  So we shouldn't map over plugins, to avoid unnecessary calls because of chunking.
  Instead we explicitly loop."
  [conn object-name plugins]
  {:pre [(string? object-name)
         (sequential? plugins)
         (every? map? plugins)
         (every? :name plugins)]}
  (loop [plugins plugins]
    (println (format "trying plugin %s to describe %s" (:name (first plugins)) object-name))
    (let [plugin (first plugins)
          describe-object-fn (:describe-object plugin)
          maybe-desc (when describe-object-fn (describe-object-fn conn object-name))]
      (if maybe-desc
        maybe-desc
        (let [more-plugins (rest plugins)]
          (when (not (empty? more-plugins)) (recur more-plugins)))))))

(defn list-schemas!
  "Get list of schemas from DB.
  Returns either list as one string or nil.
  Params:
  - conn - the JDBC connection,
  - plugins - list of plugins."
  [conn plugins]
  (loop [plugins plugins]
    (let [plugin (first plugins)
          maybe-list-schemas-fn (:list-schemas plugin)
          maybe-schemas (when maybe-list-schemas-fn (maybe-list-schemas-fn conn))]
      (if maybe-schemas
        maybe-schemas
        (let [more-plugins (rest plugins)]
          (if (not (empty? more-plugins))
            (recur more-plugins)
            (do
              (debugf "list-schemas!: No plugin knows how to do list-schemas")
              nil)))))))

(defn validate-plugin
  "Check if this thing is a plugin.
  This is a poor-man's schema validation.
  Return a collection of errors, where
  each error is a pair of:
  - optionally key path,
  - message.
  No errors means that this thing looks like plugin."
  [p]
  (let [validators [
                    (fn [p] (when-not (:name p)
                              [[:name] ":name is required"]))
                    (fn [p] (when-not (string? (:name p))
                              [[:name] ":name must be a string"]))
                    (fn [p] (when (and (not (nil? (:list-schemas p)))
                                       (not (ifn? (:list-schemas p))))
                              [[:list-schemas] ":list-schemas must be callable if present"]))
                    ]]
    (->> (for [validator validators]
           (when-let [validation-result (validator p)]
             validation-result))
         (remove nil?))))
