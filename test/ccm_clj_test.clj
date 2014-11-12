(ns ccm-clj-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [expectations :refer :all]
            [ccm-clj.impl :as impl]
            [ccm-clj :refer :all]))

(set! *warn-on-reflection* true)


(def existing (clusters))
(def current-cluster (active-cluster))
(def current-keyspace (default-keyspace current-cluster))

(defn tidy-up {:expectations-options :before-run} []
  (if current-cluster (stop!))
  (ccm-clj/remove! "ccmcljtest1")
  (ccm-clj/remove! "ccmcljtest2"))

(defn tidy-up {:expectations-options :after-run} []
  (if current-cluster (stop!))
  (if (cluster? "ccmcljtest1") (ccm-clj/remove! "ccmcljtest1"))
  (if (cluster? "ccmcljtest2") (ccm-clj/remove! "ccmcljtest2"))
  (if current-keyspace (set-default-keyspace! current-keyspace))
  (remove-savepoints! "ccmcljtest1")
  (remove-savepoints! "ccmcljtest2")
  (if (and current-cluster (cluster? current-cluster)) (switch! current-cluster)))

(defmacro expect-no-stderr [body]
  `(expect "" (:err (~@body))))

(expect-no-stderr (new! "ccmcljtest1" "2.1.0" 3 20111))

;cql as file
(expect-no-stderr (cql! (io/file "./test/resources/test-keyspace.cql")))
;cql as url, keyspace in file
(expect-no-stderr (cql! (io/resource "test-schema.cql")))
;file with given keyspace
(expect-no-stderr (cql! (io/file "./test/resources/test-data.cql") "ccmclj"))

(expect (set-default-keyspace! "ccmclj"))
;load string using default keyspace - and trim surplus ; which ccm spews on
(expect (cql! "update testtable set data = '22' where id = 2;insert into testtable (id, data) values (3, '2');")) ;

;test reader
(expect (str/trim (slurp (io/file "./test/resources/test-data2.table")))
        (str/trim (:out (cql! (io/reader "./test/resources/test-data2.query")))))

(expect (set ["node1" "node2" "node3"]) (set (:nodes (cluster-conf))))

(expect "ccmcljtest1" (active-cluster))
(expect (contains? (set (clusters)) "ccmcljtest1"))
(expect (cluster? "ccmcljtest1"))
(expect "ccmclj" (default-keyspace))

(expect (not (cluster? "ccmcljtest2")))
(expect (new! "ccmcljtest2" "2.0.9" 2 20211))
(expect "ccmcljtest2" (active-cluster))
(expect (set ["node1" "node2"]) (set (:nodes (cluster-conf))))
(expect (remove! "ccmcljtest2"))
(expect nil (active-cluster))

(expect (switch! "ccmcljtest1"))
(expect (start! "ccmcljtest1"))

(expect (hash-set "node1" "node2" "node3" "node4")
        (do (add-node! "node4" "127.0.0.4" 20115)
            (set (:nodes (cluster-conf)))))

(expect (flush! "node4"))

(expect (remove-node! "node4"))
(expect (hash-set "node1" "node2" "node3") (set (:nodes (cluster-conf))))

(expect (cql! "insert into testtable (id, data) values (99, 'tosurvive');"))
(expect (savepoint! "testsavepoint"))
(expect (cql! "insert into testtable (id, data) values (100, 'rollback');"))
(expect "id  | data\n-----+-----------\n  99 | tosurvive\n 100 |  rollback\n\n(2 rows)" (:out (cql! "select * from testtable where id in (99,100)")))
(expect (restore! "testsavepoint"))
(expect "id | data\n----+-----------\n 99 | tosurvive\n\n(1 rows)" (:out (cql! "select * from testtable where id in (99,100)")))
(expect (restore! "ccmcljtest1" "testsavepoint"))
(expect "id | data\n----+-----------\n 99 | tosurvive\n\n(1 rows)" (:out (cql! "select * from testtable where id in (99,100)")))
(expect (savepoint! "testsave1"))
(expect (savepoint! "testsave2"))
(expect (savepoint? "ccmcljtest1" "testsave2"))
(expect (savepoint? "testsave2"))
(expect (remove-savepoints!))
(expect false (savepoint? "testsave1"))
(expect false (savepoint? "ccmcljtest1" "testsave2"))
