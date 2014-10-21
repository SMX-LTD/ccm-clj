(ns ccm-clj
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [ccm-clj.impl :refer :all])
  (:import [java.net URL]
           [java.io File]))

;;;;;;;;;;;;;
;;; Public

(defn exec!
  "Execute ccm `cmd`, returns {:err <stderr messages>, :out <stdout messages>, :exit <return code>}"
  [cmd & args]
  (log/info "??")
  (apply ccm cmd args))

(defn get-active-cluster
  "Get name of active cluster"
  []
  (get-active))

(defn cluster?
  "Is `name` found in list of CCM clusters."
  [name]
  (some? (re-matches (re-pattern (str "(?s)" ".*?\\b" name "\\b.*?")) (:out (ccm "list" :quiet)))))

(defn set-default-keyspace!
  "Set default keyspace for `cluster` or the active cluster, persists across cluster switches, clears on remove."
  ([keyspace]
   (if (ensure-active)
     (set-default-keyspace! (get-active-cluster) keyspace)))
  ([cluster keyspace]
   (if (cluster? cluster)
     (swap! default-keyspaces assoc cluster keyspace)
     (log/error "No cluster " cluster " to set default keyspace on"))))

(defn get-default-keyspace
  "Get name of default keyspace of `cluster` or active cluster"
  ([]
   (if (ensure-active)
     (@default-keyspaces (get-active-cluster))))
  ([cluster]
   (@default-keyspaces cluster)))

(defn get-cluster-conf
  "Get a map of the cluster conf"
  ([]
   (if (ensure-active)
     (get-cluster-conf (get-active-cluster))))
  ([name]
   (conf-as-map (io/file ccm-dir name "cluster.conf"))))

(defn get-node-conf
  "Get a map of the conf for the node `name` from active cluster"
  ([name]
   {:pre (= (get-active-cluster) nil)}
   (conf-as-map (io/file ccm-dir (get-active-cluster) name (str name ".conf")))))

(defn start!
  "Start CCM cluster `name`."
  ([]
   (if (ensure-active)
     (start! (get-active-cluster))))
  ([name]
   (let [result (ccm "start")]
     (log/info (str name " cluster started"))
     result)))

(defn cql!
  "Execute cqlsh cmd (against 'node1') in keyspace `keyspace` from cmd-source (can be File, String or URL) into active cluster.
  Note this is a convienent way of loading schemas and seed data, and shouldn't be used in place of a proper CQL client like Alia or Cassaforte."
  ([cmd-source]
   (cql! cmd-source (@default-keyspaces (get-active-cluster)) "" "node1"))
  ([cmd-source keyspace]
   (cql! cmd-source keyspace "" "node1"))
  ([cmd-source keyspace log-name]
   (cql! cmd-source keyspace log-name "node1"))
  ([cmd-source keyspace log-name node-name]
   (let [thing cmd-source
         value (as-cqlsh-arg thing)
         result (apply ccm (concat (if keyspace [node-name "cqlsh" "-k" keyspace] [node-name "cqlsh"]) value))]
     (log/info (str log-name "load finished of " (to-str cmd-source) " (check for errors in output)"))
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
  (if (ensure-active)
    (let [result (ccm "stop")]
      (log/info (str (get-active-cluster) " cluster stopped"))
      result)
    (log/info "No active cluster to stop")))

(defn flush!
  "If `node-name` flush that node on active cluster, else flush all nodes"
  ([]
   (let [result (ccm "flush")]
     (log/info (str (get-active-cluster) " node flushed"))
     result))
  ([node-name]
   (let [result (ccm "flush")]
     (log/info (str node-name " node flushed"))
     result)))

(defn remove!
  "Remove cluster `name` from CCM including ALL DATA, resetting active cluster if `name`"
  [name]
  (let [result (ccm "remove" name)]
    (swap! default-keyspaces dissoc name)
    (log/info (str name " cluster removed"))
    result))

(defn add-node!
  "Add node `node-name` at next available loopback to active cluster."
  ([node-name ip ports-spec]
   (if (and (ensure-active) (not (or ports-spec (:cql ports-spec))))
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
     (log/info "Added node" node-name "@" (str ip ":" cql)))))

(defn remove-node! [node-name]
  "Remove node `node-name` from active cluster."
  (if (ensure-active)
    (ccm node-name "remove")))

(defn new!
  "Create and start cluster.
  `num-nodes` Cassandra nodes will be running using ports inferred from ports-spec
  Those loopbacks may need to be aliased depending on OS.
  A an optional map `ports-spec` can be used to supply some of :cql :storage :thift :jmx port,
  else ports from cql-port to cql-port + 3 will be used."
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
           (add-node! node-name ip {:cql cql})
           (log/info "Added node" node-name "at" ip ":" cql)))
       (log/info "Found existing cluster at" cluster-dir)))
   (start! name)
   (switch! name)))

(defn savepoint?
  "Is `name` a savepoint of active cluster or `cluster`."
  ([name]
   (if (ensure-active)
     (savepoint? (get-active-cluster) name)))
  ([cluster name]
   (.exists (io/file savepoint-dir cluster name))))

(defn savepoint!
  "Create savepoint `name` from `cluster` or active cluster that can be reset to via `rollback!`."
  ([name]
   (if (ensure-active)
     (savepoint! (get-active-cluster) name)
     false))
  ([cluster name]
   (let [cluster-savepoint-dir (io/file savepoint-dir cluster)]
     (if-not (.exists cluster-savepoint-dir)
       (.mkdirs cluster-savepoint-dir))
     (let [save-dir (io/file cluster-savepoint-dir name)
           cluster-dir (io/file ccm-dir cluster)]
       (flush!)
       (if (copy-dir cluster-dir save-dir)
         (do (log/info "Created savepoint" name "in cluster" cluster)
             true)
         (do (log/error "Failed to create savepoint" name "in cluster " cluster)
             (del-dir save-dir)
             false))))))

(defn restore!
  "Rollback active cluster or `cluster` to savepoint `name` and start the cluster"
  ([savepoint]
   (if (ensure-active)
     (restore! (get-active-cluster) savepoint)))
  ([cluster savepoint]
   (let [save-dir (io/file savepoint-dir cluster savepoint)
         cluster-dir (io/file ccm-dir cluster)]
     (if (get-active-cluster) (stop!))
     (if (sync-dir save-dir cluster-dir)
       (do (log/info "Restored to savepoint" savepoint)
           (start! cluster)
           true)
       (do (log/error "Failed to rollback to savepoint" savepoint)
           false)))))

(defn remove-savepoint!
  "Remove `savepoint` from active cluster or `cluster` if supplied"
  ([savepoint]
   (if (ensure-active)
     (remove-savepoint! (get-active-cluster) savepoint)))
  ([cluster savepoint]
   (let [save-dir (io/file savepoint-dir cluster savepoint)]
     (if (del-dir save-dir)
       (do (log/info "Deleted savepoint" savepoint "in cluster" cluster)
           true)
       (do (log/error "Failed to delete savepoint" savepoint "in cluster" cluster)
           false)))))

(defn remove-savepoints!
  "Remove all savepoints from active cluster or `cluster` if supplied"
  ([]
   (if (ensure-active)
     (remove-savepoints! (get-active-cluster))))
  ([cluster]
   (let [save-dir (io/file savepoint-dir cluster)]
     (if (del-dir save-dir)
       (do (log/info "Deleted savepoints for" cluster)
           true)
       (do (log/error "Failed to deleted savepoints for" cluster)
           false)))))