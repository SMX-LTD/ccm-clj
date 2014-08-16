(ns ccm-clj
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [ccm-clj.impl :refer :all])
  (:import [java.net URL]
           [java.io File]))

;;;;;;;;;;;;;
;;; Public

(def default-keyspaces (atom {}))
(def default-base-port 19100)
(def jmx-increment (atom 100))

(defn exec!
  "Execute ccm `cmd`, returns {:err <stderr messages>, :out <stdout messages>, :exit <return code>}"
  [cmd & args]
  (log/info "??")
  (apply ccm cmd args))

(defn get-active-cluster
  "Get name of active cluster"
  []
  (if (.exists ^File (io/file ccm-dir "CURRENT"))
    (str/trim (slurp (io/file ccm-dir "CURRENT")))
    nil))

(defn cluster?
  "Is `name` found in list of CCM clusters."
  [name]
  (re-matches (re-pattern (str "(?s)" ".*?\\b" name "\\b.*?")) (:out (ccm "list" :quiet))))

(defn set-default-keyspace!
  "Set default keyspace for `cluster` or the active cluster, persists across cluster switches, clears on remove."
  ([keyspace]
   (if get-active-cluster
     (swap! default-keyspaces assoc (get-active-cluster) keyspace)
     (log/error "No active cluster to set default keyspace on")))
  ([cluster keyspace]
   (if (cluster? cluster)
     (swap! default-keyspaces assoc cluster keyspace)
     (log/error "No cluster " cluster " to set default keyspace on"))))

(defn get-default-keyspace
  "Get name of default keyspace of `cluster` or active cluster"
  ([]
   (@default-keyspaces (get-active-cluster)))
  ([cluster]
   (@default-keyspaces cluster)))

(defn get-cluster-conf
  "Get a map of the cluster conf"
  ([]
   (if (get-active-cluster) (get-cluster-conf (get-active-cluster)) nil))
  ([name]
   (conf-as-map (io/file ccm-dir name "cluster.conf"))))

(defn get-node-conf
  "Get a map of the conf for the node `name` from active cluster"
  ([name]
   {:pre (= (get-active-cluster) nil)}
   (conf-as-map (io/file ccm-dir (get-active-cluster) name (str name ".conf")))))

(defn start!
  "Start CCM cluster `name`."
  [name]
  (let [result (ccm "start")]
    (log/info (str name " cluster started"))
    result))

(defn cql!
  "Execute cqlsh cmd (against 'node1') in keyspace `keyspace` from cmd-source (can be File, String or URL) into active cluster.
  Note this is a convienent way of loading schemas and seed data, and shouldn't be used in place of a proper CQL client like Alia or Cassaforte."
  ([cmd-source]
   (cql! cmd-source (@default-keyspaces get-active-cluster) "" "node1"))
  ([cmd-source keyspace]
   (cql! cmd-source keyspace "" "node1"))
  ([cmd-source keyspace log-name]
   (cql! cmd-source keyspace log-name "node1"))
  ([cmd-source keyspace log-name node-name]
   (let [thing cmd-source
         result (apply ccm (concat (if keyspace [node-name "cqlsh" "-k" keyspace] [node-name "cqlsh"]) (as-cqlsh-arg thing)))]
     (log/info (str log-name " load finished of " (to-str cmd-source) " (check for errors in output)"))
     result)))

(defn switch!
  "Switch active cluster to `name`"
  [name]
  (let [result (ccm "switch" name)]
    (log/info (str "Switch active cluster to " name))
    result))

(defn get-clusters
  "Returns a vector of clusters in CCM"
  []
  (vec (re-seq #"[^\n\s*]+" (:out (ccm "list" :quiet)))))

(defn stop!
  "Stop the current CCM cluster."
  []
  (if (get-active-cluster)
    (let [result (ccm "stop")]
      (log/info (str (get-active-cluster) " cluster stopped"))
      result)
    (log/info "No active cluster to stop")))

(defn remove!
  "Remove cluster `name` from CCM including ALL DATA, resetting active cluster if `name`"
  [name]
  (let [result (ccm "remove" name)]
    (swap! default-keyspaces dissoc name)
    (log/info (str name " cluster removed"))
    result))

(defn add-node!
  "Add node `node-name` at next available loopback from active cluster."
  ([node-name ip ports-spec]
   (if (not (or ports-spec (:cql ports-spec)))
     (throw (IllegalArgumentException. "Ports spec must be  a cql port OR map containing :cql port")))

   (let [ports-spec (if-not (map? ports-spec) {:cql ports-spec} ports-spec)
         cql (:cql ports-spec)
         {:keys [storage thrift jmx]
          :or   {storage (+ 1 cql)
                 thrift  (+ 2 cql)
                 jmx     (+ @jmx-increment cql)}} ports-spec]
     (swap! jmx-increment + 10)
     (ccm "add"
          "-r" "0"
          "-j" (str jmx)                                    ;NO IP!
          "-l" (str ip ":" storage)
          "-t" (str ip ":" thrift)
          (str "--binary-itf=" (str ip ":" cql))
          node-name)
     (log/info "Added node " node-name "@" (str ip ":" cql)))))


(defn remove-node! [node-name]
  "Remove node `node-name` from active cluster."
  (ccm node-name "remove"))

(defn new!
  "Create and start cluster.
  `num-nodes` Cassandra nodes will be running using ports inferred from ports-spec
  Those loopbacks may need to be aliased depending on OS.
  Ports from cql-port to cql-port + 3 will be used."

  ([name version num-nodes]
   (new! name version num-nodes {}))
  ([name version num-nodes ports-spec]
   (let [ports-spec (merge
                      {:cql default-base-port}
                      (if (map? ports-spec) ports-spec {:cql ports-spec}))
         cluster-dir (io/file ccm-dir name)]
     (if (not (cluster? name))
       (do
         (log/info "Creating new cluster" name)
         (ccm "create" name "-v" (str version))
         (doseq [i (range 1 (inc num-nodes))
                 :let [ip (str "127.0.0." i)
                       node-name (str "node" i)
                       cql (:cql ports-spec)]]
           (add-node! node-name ip {:cql cql})))
       (log/info "Found existing cluster at" cluster-dir)))
   (start! name)
   (switch! name)))

