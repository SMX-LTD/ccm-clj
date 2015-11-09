(defproject com.smxemail/ccm-clj "1.1.0"
  :description "Clojure interface to Cassandra Cluster Manager"
  :min-lein-version "2.0.0"
  :url "https://github.com/SMX-LTD/ccm-clj"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[com.climate/java.shell2 "0.1.0"]
                 [org.clojure/java.classpath "0.2.2"]
                 [org.clojure/tools.logging "0.3.1"]]
  :plugins [[lein-expectations "0.0.8"]
            [lein-kibit "0.0.8" :exclusions [org.clojure/tools.cli org.clojure/tools.namespace]]
            [com.ambrosebs/dynalint "0.1.3"]
            [lein-dynalint "0.1.4"]
            [lein-ancient "0.5.4" :exclusions [commons-codec]]
            [lein-cloverage "1.0.2"]
            [jonase/eastwood "0.2.1" :exclusions [org.clojure/clojure]]]
  :repositories {"sonatype"           {:url       "http://oss.sonatype.org/content/repositories/releases"
                                       :snapshots false
                                       :releases  {:checksum :fail :update :always}}
                 "sonatype-snapshots" {:url       "http://oss.sonatype.org/content/repositories/snapshots"
                                       :snapshots true
                                       :releases  {:checksum :fail :update :always}}}
  :profiles {:1.6    {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :master {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :dev    {:source-paths   ["dev"]
                      :resource-paths ["test/resources"]
                      :dependencies   [[expectations "2.1.1"]
                                       [org.clojure/clojure "1.7.0"]
                                       [ch.qos.logback/logback-classic "1.1.3"]]}}
  :aliases {"all" ["with-profile" "+dev:+1.5:+1.6:+master"]})
