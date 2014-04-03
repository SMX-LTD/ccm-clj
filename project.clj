(defproject com.smxemail/ccm-clj "0.1.0"
  :description "Clojure interface to Cassandra Cluster Manager"
  :url "https://github.com/SMX-LTD/ccm-clj"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.climate/java.shell2 "0.1.0"]
                 [org.clojure/tools.logging "0.2.6"]]
  :plugins [[lein-expectations "0.0.8"]]
  :profiles {:dev {:source-paths ["dev"]
                   :resource-paths ["test/resources"]
                   :dependencies [[expectations "2.0.7"]]}})
