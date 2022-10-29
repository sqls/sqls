(defproject sqls "0.1.12"
  :description "SQLS"
  :url "https://github.com/mpietrzak/sqls"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[com.fifesoft/rsyntaxtextarea "3.3.0"]
                 [com.github.oshi/oshi-core "6.3.0"]
                 [com.taoensso/timbre "6.0.1"]
                 [fipp "0.6.26"]
                 [io.aviso/pretty "1.3"]
                 [com.mchange/c3p0 "0.9.5.5"]
                 [org.clojure/clojure "1.11.1"]
                 [org.clojure/data.json "2.4.0"]
                 [org.clojure/java.jdbc "0.7.12"]
                 [org.clojure/tools.cli "1.0.214"]
                 [org.clojure/tools.logging "1.2.4"]
                 [org.xerial/sqlite-jdbc "3.39.3.0"]
                 [seesaw "1.5.0"]]
  :main sqls.core
  :java-source-paths ["src"]
  :javac-options ["-target" "11" "-source" "11"]
  :jvm-opts ["-Dclojure.compiler.direct-linking=true"
             "-Xms4M"
             "-Xmx16G"
             "-XX:+UseZGC"
             "-XX:SoftMaxHeapSize=4G"
             "-XX:ZUncommitDelay=15"
             "-verbose:gc"]
  :target-path "target/%s"
  :plugins [[lein-codox "0.10.8"]]
  :codox {:output-dir "doc/codox"}
  :profiles {:uberjar {:aot :all}
             :repl {:dependencies [[org.clojure/tools.namespace "1.3.0"]]}
             :dev {:dependencies [[org.clojure/tools.namespace "1.3.0"]]
                   :global-vars {*warn-on-reflection* true}
                   :jvm-opts ["-Xms4M"
                              "-Xmx16G"
                              "-XX:+UseZGC"
                              "-XX:SoftMaxHeapSize=4G"
                              "-XX:ZUncommitDelay=15"
                              "-verbose:gc"]}})
