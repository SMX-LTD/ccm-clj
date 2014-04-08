# ccm-clj

A Clojure (1.4+) interface to Cassandra Cluster Manager (https://github.com/pcmanus/ccm) suitable for use in integration tests.

## Current Version

Artifacts are released to Clojars. If you are using Maven, add the following repository definition to your pom.xml:

```xml
<repository>
  <id>clojars.org</id>
  <url>http://clojars.org/repo</url>
</repository>
```

With Leiningen:

```clojure
[com.smxemail/ccm-clj "0.1.1"]
```

With Maven:

```xml
<dependency>
  <groupId>com.smxemail</groupId>
  <artifactId>ccm-clj</artifactId>
  <version>0.1.1</version>
</dependency>
```

## Usage

```clojure
(if (not (ccm/cluster? "testcluster"))
  (do
    (ccm/new! "testcluster" cass-version num-nodes cql-port)
    (ccm/cql! (io/file "./test/resources/test-keyspace.cql"))
    (ccm/cql! (io/resource "schema/test-schema.cql") "testkeyspace")
    (ccm/cql! (io/file "./test/resources/test-data.cql") "testkeyspace"))
  (do
    (ccm/switch! "testcluster")
    (ccm/start! "testcluster")))
....
(ccm/remove! "testcluster")
```

Notes:

- Ports from cql-port to cql-port+3 will be assigned to cql, thrift, jmx and storage respectively.
- If you abort without cleanup, you may leave CassandraDaemon's running which you can stop from the repl  `(ccm/stop!)` or in the shell `ccm stop`.

For more see ;; Public in [`src/ccm_clj.clj`](src/ccm_clj.clj#L81) or tests in [`test/ccm_clj_test.clj`](test/ccm_clj_test.clj).

## Tests Usage

```clojure
lein all expectations
```

## Contact

Colin Taylor at gmail.

## License

Copyright © 2014 SMX Ltd (http://smxemail.com) and Contributors.

Distributed under the Eclipse Public License.

