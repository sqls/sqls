(ns sqls.plugin)

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
  Note that each plugin can support many classes."
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
