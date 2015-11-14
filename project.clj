(defproject sqls "0.1.3"
  :description "SQLS"
  :url "https://github.com/mpietrzak/sqls"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[io.aviso/pretty "0.1.19"]
                 [org.clojure/clojure "1.8.0-RC1"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/java.jdbc "0.4.2"]
                 [org.clojure/tools.cli "0.3.3"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.xerial/sqlite-jdbc "3.8.11.2"]
                 [seesaw "1.4.5"]]
  :main sqls.core
  :java-source-paths ["src"]
  :javac-options ["-target" "1.7" "-source" "1.7"]
  :target-path "target/%s"
  :plugins [[codox "0.8.13"]]
  :codox {:output-dir "doc/codox"}
  :profiles {:uberjar {:aot :all}
             :dev {:global-vars {*warn-on-reflection* true}
                   :jvm-opts ["-Xms1G" "-Xmx4G" "-XX:+PrintGC" "-XX:+PrintGCDateStamps" "-XX:+UseG1GC" "-XX:MaxGCPauseMillis=1"]}})
