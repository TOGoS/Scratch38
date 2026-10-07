# Scratch38-S0032: A Maven Plugin

Goal: Plugin to compile Java code, somehow.

For now the only goal is `hello`, which writes a greeting to a file.

## Coordinates

| | |
|-|-|
| groupId    | `net.nuke24.scratch38.s0032`     |
| artifactId | `scratch38-s0032-maven-plugin`   |
| version    | `0.1.1-SNAPSHOT`                 |
| goalPrefix | `scratch38-s0032`                |

First build and install it into your local repository (`~/.m2`) so other
projects can resolve it:

```sh
cd Scratch38-S0032
mvn install
```

## Running directly from the command line

You don't need any project configuration to invoke a goal directly. Use the
fully-qualified `groupId:artifactId:version:goal` form:

```sh
mvn net.nuke24.scratch38.s0032:scratch38-s0032-maven-plugin:0.1.1-SNAPSHOT:hello
```

Override parameters with `-D`:

```sh
mvn net.nuke24.scratch38.s0032:scratch38-s0032-maven-plugin:0.1.1-SNAPSHOT:hello \
    -Dscratch38-s0032.greeting="Hi there" \
    -Dscratch38-s0032.outputFile=greeting.txt
```

If you add this plugin's groupId to `pluginGroups` in your `~/.m2/settings.xml`,
you can use the short prefix form instead:

```xml
<settings>
  <pluginGroups>
    <pluginGroup>net.nuke24.scratch38.s0032</pluginGroup>
  </pluginGroups>
</settings>
```

```sh
mvn scratch38-s0032:hello
```

## Using it from another project's `pom.xml`

Declare the plugin in the consuming project's `<build><plugins>` section. Bind
the `hello` goal to a lifecycle phase via an `<execution>` so it runs
automatically as part of a normal build:

```xml
<build>
	<plugins>
		<plugin>
			<groupId>net.nuke24.scratch38.s0032</groupId>
			<artifactId>scratch38-s0032-maven-plugin</artifactId>
			<version>0.1.1-SNAPSHOT</version>
			<executions>
				<execution>
					<id>say-hello</id>
					<!-- Optional: defaults to generate-resources (the Mojo's defaultPhase). -->
					<phase>generate-resources</phase>
					<goals>
						<goal>hello</goal>
					</goals>
					<configuration>
						<greeting>Hello from the build!</greeting>
						<outputFile>${project.build.directory}/hello.txt</outputFile>
					</configuration>
				</execution>
			</executions>
		</plugin>
	</plugins>
</build>
```

Now running any build that reaches the bound phase will execute the goal:

```sh
mvn generate-resources   # or: mvn package, mvn install, etc.
```

You can also leave out the `<executions>` block and invoke `mvn
scratch38-s0032:hello` on demand; the `<configuration>` on the plugin (outside
any execution) then supplies the default parameter values.
