# CCM-clj

A Clojure (1.5+) interface to Cassandra Cluster Manager (https://github.com/pcmanus/ccm) suitable for use in integration tests.

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
[com.smxemail/ccm-clj "0.1.7"]
```

With Maven:

```xml
<dependency>
  <groupId>com.smxemail</groupId>
  <artifactId>ccm-clj</artifactId>
  <version>0.1.7</version>
</dependency>
```

### CCM installation

See Requirements and Installation at https://github.com/pcmanus/ccm

Note that local loopback aliasing may be required on OSX.
I have /Library/LaunchDaemons/moreloopbacks.plist:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>Label</key>
	<string>MORE_LOOPBACKS</string>
	<key>ProgramArguments</key>
	<array>
		<string>/usr/local/scripts/moreloopbacks.sh</string>
	</array>
	<key>RunAtLoad</key>
	<true/>
	<key>UserName</key>
	<string>ROOT</string>
	<key>GroupName</key>
	<string>WHEEL</string>
</dict>
</plist>
```

And then /usr/local/scripts/moreloopbacks.sh:
```bash
#!/bin/bash
for ((i=2;i<6;i++))
do
  sudo ifconfig lo0 alias 127.0.0.$i up
done
```

## Usage

```clojure

(ns some-cas-test
  (:require [ccm-clj :as ccm]
            [clojure.java.io :as io]))

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

## Notes

- This is NOT A CQL CLIENT it's for test cluster setup. Instead, use [`Alia`](https://github.com/mpenet/alia) or [`Cassaforte`](https://github.com/clojurewerkz/cassaforte).
- Similarly I don't intend to shadow every CCM function only to provide a useful integration API.
- I test new releases on the current CCM, so you should upgrade CCM-clj and CCM at the same time.
- Probably doesn't work on Windows. Patches welcome.
- IPs from 127.0.0.1 to 127.0.0.n will be used, this may require loopback aliasing see Installation.
- Ports from cql-port to cql-port+3 will be assigned to CQL, thrift, JMX and storage respectively.
- If you abort without cleanup, you may leave CassandraDaemon(s) running which you can stop from the repl `(ccm/stop!)` or in the shell `ccm stop`.
- Using older versions of Cassandra may require Snappy-Java to be an explicit dependency due to https://github.com/xerial/snappy-java/issues/6
- Error: Could not find or load main class org.apache.cassandra.service.CassandraDaemon might mean an interrupted download, try blowing away .ccm.

For more see the API in [`src/ccm_clj.clj`](src/ccm_clj.clj) or tests in [`test/ccm_clj_test.clj`](test/ccm_clj_test.clj).

## Tests Usage

```clojure
lein all expectations
```

## Contact

Colin Taylor at gmail.

## License

Copyright © 2014 SMX Ltd (http://smxemail.com) and Contributors.

Distributed under the Eclipse Public License.

