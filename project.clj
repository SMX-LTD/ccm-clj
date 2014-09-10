(defproject com.smxemail/ccm-clj "0.1.6"
  :description "Clojure interface to Cassandra Cluster Manager"
  :min-lein-version "2.0.0"
  :url "https://github.com/SMX-LTD/ccm-clj"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.climate/java.shell2 "0.1.0"]
                 [org.clojure/tools.logging "0.2.6"]]
  :plugins [[lein-expectations "0.0.8"]]
  :repositories {"sonatype"           {:url       "http://oss.sonatype.org/content/repositories/releases"
                                       :snapshots false
                                       :releases  {:checksum :fail :update :always}}
                 "sonatype-snapshots" {:url       "http://oss.sonatype.org/content/repositories/snapshots"
                                       :snapshots true
                                       :releases  {:checksum :fail :update :always}}}
  :profiles {:1.5    {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6    {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :master {:dependencies [[org.clojure/clojure "1.7.0-alpha1"]]}
             :dev    {:source-paths   ["dev"]
                      :resource-paths ["test/resources"]
                      :dependencies   [[expectations "2.0.9"]
                                       [ch.qos.logback/logback-classic "1.0.13"]
                                       [ch.qos.logback/logback-core "1.0.13"]]}}
  :aliases {"all" ["with-profile" "+dev:+1.5:+1.6:+master"]})
