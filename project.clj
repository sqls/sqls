(defproject sqls "0.1.0-SNAPSHOT"
  :description "SQLS"
  :url "https://github.com/mpietrzak/sqls"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [
                 [org.clojure/clojure "1.7.0-alpha2"]
                 [org.clojure/data.json "0.2.5"]
                 [org.clojure/java.jdbc "0.3.5"]
                 [org.clojure/tools.cli "0.3.1"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.xerial/sqlite-jdbc "3.7.2"]
                 [seesaw "1.4.4"]
                 ]
  :main sqls.core
  :java-source-paths ["src"]
  :target-path "target/%s"
  :plugins [[codox "0.8.10"]
            [lein-ancient "0.5.5"]]
  :codox {:output-dir "doc/codox"}
  :profiles {:uberjar {:aot :all}
             :dev {:global-vars {*warn-on-reflection* true}
                   :jvm-opts ["-Xms4M" "-Xmx1G" "-XX:+PrintGC" "-XX:+UseG1GC"]}})
