(defproject dpgdb "0.1.0"
  :description "FIXME: write description"
  :url "https://github.com/gustavo-depaula/dpgdb"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.10.0"]]
  :main ^:skip-aot dpgdb.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
