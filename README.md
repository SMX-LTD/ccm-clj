# ccm-clj

A Clojure interface to Cassandra Cluster Manager (https://github.com/pcmanus/ccm) suitable for use in integration tests.

## Usage

```clojure
(if (not (ccm/cluster? "testcluster"))
  (do
    (ccm/new! "testcluster" "2.0.4" 3 (get-in cfg [:cql-port]))
    (ccm/cql! (io/file "./test/resources/test-keyspace.cql") nil "Keyspace")
    (ccm/cql! (io/resource "schema/test-schema.cql") "mailtest" "Schema")
    (ccm/cql! (io/file "./test/resources/test-data.cql") "mailtest" "Data"))
  (do
    (ccm/switch! "testcluster")
    (ccm/start! "testcluster")))
```

## License

Copyright © 2014 SMX Ltd (http://smxemail.com) and Contributors.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
