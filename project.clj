(defproject sqls "0.1.11"
  :description "SQLS"
  :url "https://github.com/mpietrzak/sqls"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[com.fifesoft/rsyntaxtextarea "3.1.1"]
                 [com.taoensso/timbre "5.1.0"]
                 [fipp "0.6.23"]
                 [io.aviso/pretty "0.1.37"]
                 [org.clojure/clojure "1.10.1"]
                 [org.clojure/data.json "1.0.0"]
                 [org.clojure/java.jdbc "0.7.11"]
                 [org.clojure/tools.cli "1.0.194"]
                 [org.clojure/tools.logging "1.1.0"]
                 [org.xerial/sqlite-jdbc "3.32.3.2"]
                 [seesaw "1.5.0"]]
  :main sqls.core
  :java-source-paths ["src"]
  :javac-options ["-target" "11" "-source" "11"]
  :jvm-opts ["-Dclojure.compiler.direct-linking=true"]
  :target-path "target/%s"
  :plugins [[lein-codox "0.10.7"]]
  :codox {:output-dir "doc/codox"}
  :profiles {:uberjar {:aot :all}
             :repl {:dependencies [[org.clojure/tools.namespace "1.0.0"]]}
             :dev {:dependencies [[org.clojure/tools.namespace "1.0.0"]]
                   :global-vars {*warn-on-reflection* true}
                   :jvm-opts ["-Xms4M"
                              "-Xmx4G"
                              "-XX:+UseG1GC"
                              "-XX:MaxGCPauseMillis=10"]}})
