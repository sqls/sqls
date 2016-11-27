(defproject sqls "0.1.10"
  :description "SQLS"
  :url "https://github.com/mpietrzak/sqls"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[com.fifesoft/rsyntaxtextarea "2.6.0"]
                 [fipp "0.6.7"]
                 [io.aviso/pretty "0.1.32"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/java.jdbc "0.6.1"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.xerial/sqlite-jdbc "3.15.1"]
                 [seesaw "1.4.5"]]
  :main sqls.core
  :java-source-paths ["src"]
  :javac-options ["-target" "1.7" "-source" "1.7"]
  :jvm-opts ["-Dclojure.compiler.direct-linking=true"]
  :target-path "target/%s"
  :plugins [[lein-codox "0.10.2"]]
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
