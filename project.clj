(defproject sqls "0.1.0-SNAPSHOT"
  :description "SQLS"
  :url "https://github.com/mpietrzak/sqls"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0-alpha6"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/java.jdbc "0.3.6"]
                 [org.clojure/tools.cli "0.3.1"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.xerial/sqlite-jdbc "3.8.7"]
                 ;[com.taoensso/timbre "3.4.0"]
                 [seesaw "1.4.5"]
                 ]
  :main sqls.core
  :java-source-paths ["src"]
  :target-path "target/%s"
  :plugins [[codox "0.8.11"]]
  :codox {:output-dir "doc/codox"}
  :profiles {:uberjar {:aot :all}
             :dev {:global-vars {*warn-on-reflection* true}
                   :jvm-opts ["-Xms4M" "-Xmx1G" "-XX:+PrintGC" "-XX:+UseG1GC"]}})
