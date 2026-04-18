# Getting Started

A SCIF contract can be compiled with the SCIF compiler.

## Compile SCIF via JAR

Download the pre-built SCIF JAR from: https://github.com/apl-cornell/SCIF/releases/download/latest/SCIF.jar and run:

```
java -ea -jar SCIF.jar -c [path_to_SCIF_contract]
```

## Compile SCIF via Source

### Prerequisite

To build the SCIF compiler, the following packages are required:

* JFlex
* Java 21 or later
* Ant
* Gradle

### Installing the SCIF compiler

SCIF project is hosted at [github](https://github.com/apl-cornell/SCIF) and can be built with the help of gradle. The following instructions clone the SCIF project and build it using gradle.

```shell
git clone --recurse-submodules  https://github.com/apl-cornell/SCIF.git
cd SCIF
./gradlew build
```

### Compiling an example SCIF program

The following instruction compiles a short SCIF example:

```shell
./scif -c test/contracts/basic/MethodDefinition.scif
```
