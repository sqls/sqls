(defproject sqls "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[seesaw "1.4.4"]
                 [org.clojure/data.json "0.2.4"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.clojure/clojure "1.6.0-beta1"]]
  :main ^:skip-aot sqls.core
  :target-path "target/%s"
  :plugins [[codox "0.6.6"]]
  :profiles {:uberjar {:aot :all}})
