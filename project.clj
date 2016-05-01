(defproject sqls "0.1.6"
  :description "SQLS"
  :url "https://github.com/mpietrzak/sqls"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[io.aviso/pretty "0.1.26"]
                 [fipp "0.6.5"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/java.jdbc "0.5.8"]
                 [org.clojure/tools.cli "0.3.3"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.xerial/sqlite-jdbc "3.8.11.2"]
                 [seesaw "1.4.5"]]
  :main sqls.core
  :java-source-paths ["src"]
  :javac-options ["-target" "1.7" "-source" "1.7"]
  :target-path "target/%s"
  :plugins [[lein-codox "0.9.5"]]
  :codox {:output-dir "doc/codox"}
  :profiles {:uberjar {:aot :all}
             :repl {:dependencies [[org.clojure/tools.namespace "0.2.11"]]}
             :dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]]
                   :global-vars {*warn-on-reflection* true}
                   :jvm-opts ["-Xms16M" "-Xmx4G" "-XX:+PrintGC" "-XX:+PrintGCDateStamps" "-XX:+UseG1GC" "-XX:MaxGCPauseMillis=1"]}})
