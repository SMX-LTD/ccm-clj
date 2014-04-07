(defproject com.smxemail/ccm-clj "0.1.1-SNAPSHOT"
  :description "Clojure interface to Cassandra Cluster Manager"
  :min-lein-version "2.0.0"
  :url "https://github.com/SMX-LTD/ccm-clj"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.climate/java.shell2 "0.1.0"]
                 [org.clojure/tools.logging "0.2.6"]]
  :plugins [[lein-expectations "0.0.8"]]
  :profiles {:1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :dev {:source-paths   ["dev"]
                   :resource-paths ["test/resources"]
                   :dependencies   [[expectations "2.0.7"]]}}
  :aliases  {"all" ["with-profile" "+dev:+1.4:+1.5:+master"]})
