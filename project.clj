(defproject sqls "0.1.10"
  :description "SQLS"
  :url "https://github.com/mpietrzak/sqls"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[com.fifesoft/rsyntaxtextarea "2.6.1"]
                 [com.taoensso/timbre "4.10.0"]
                 [fipp "0.6.9"]
                 [io.aviso/pretty "0.1.34"]
                 [org.clojure/clojure "1.9.0-alpha17"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/java.jdbc "0.7.0"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/tools.logging "0.4.0"]
                 [org.xerial/sqlite-jdbc "3.19.3"]
                 [seesaw "1.4.5"]]
  :main sqls.core
  :java-source-paths ["src"]
  :javac-options ["-target" "1.7" "-source" "1.7"]
  :jvm-opts ["-Dclojure.compiler.direct-linking=true"]
  :target-path "target/%s"
  :plugins [[lein-codox "0.10.3"]]
  :codox {:output-dir "doc/codox"}
  :profiles {:uberjar {:aot :all}
             :repl {:dependencies [[org.clojure/tools.namespace "0.2.11"]]}
             :dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]]
                   :global-vars {*warn-on-reflection* true}
                   :jvm-opts ["-Xms16M"
                              "-Xmx4G"
                              "-XX:+PrintGC"
                              "-XX:+PrintGCDateStamps"
                              "-XX:+UseG1GC"
                              "-XX:MaxGCPauseMillis=1"]}})
