# Third-party licenses

RedoReplicator declares the following runtime and build dependencies. Canonical
license texts are stored beside this file. Distribution builds also extract
every LICENSE, NOTICE and third-party notice embedded in the exact resolved
runtime jars into `resolved/<artifact>/`.

| Component | Selected license | Project |
|---|---|---|
| H2 Database | MPL-2.0 | https://github.com/h2database/h2database |
| Oracle JDBC | Oracle Free Use Terms and Conditions | https://www.oracle.com/database/technologies/maven-central-guide.html |
| Jackson | Apache-2.0 | https://github.com/FasterXML/jackson |
| Picocli | Apache-2.0 | https://github.com/remkop/picocli |
| SLF4J | MIT | https://www.slf4j.org/license.html |
| Logback | LGPL-2.1 | https://logback.qos.ch/license.html |
| JUnit 5 | EPL-2.0 | https://github.com/junit-team/junit5 |
| SnakeYAML | Apache-2.0 | https://bitbucket.org/snakeyaml/snakeyaml |
| CycloneDX Maven plugin | Apache-2.0 | https://github.com/CycloneDX/cyclonedx-maven-plugin |

Oracle JDBC is redistributed unmodified. Its embedded Oracle Free Use Terms and
Conditions are extracted from `META-INF/license.txt` and retained in every
runtime distribution.

Canonical texts:

- `Apache-2.0.txt`: Jackson, Picocli, SnakeYAML and CycloneDX Maven plugin.
- `MPL-2.0.txt`: H2 Database selected license.
- `LGPL-2.1.txt`: Logback selected license.
- `MIT.txt`: SLF4J.
- `EPL-2.0.txt`: JUnit build and test dependency.
