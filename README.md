# SCIF

You can download a prebuilt Jar at https://github.com/apl-cornell/SCIF/releases/download/latest/SCIF.jar. This is automatically updated on every push to the master branch, so this is equivalent to checking out the repository and building the Jar yourself.

Run the Jar with:

```
java -ea -jar SCIF.jar <args>
```

## Usage

```console
Usage: SCIF [-t | -p | -l | -c] [-hV] [-debug] [-lg=<m_outputFileNames>...]...
            [-o=<m_solFileNames>...]... <m_inputFiles>...
A set of tools for a new smart contract language with information flow control,
SCIF.
      <m_inputFiles>...     The source code file(s).
  -c, --compiler            Compile to Solidity (default)
      -debug
  -h, --help                Show this help message and exit.
  -l, --lexer               Tokenize
      -lg=<m_outputFileNames>...
                            The log file.
  -o=<m_solFileNames>...    The output file.
  -p, --parser              Parse: ast json as log
  -t, --typechecker         Information flow typecheck: constraints as log
  -V, --version             Print version information and exit.
```

## Build from the JAR (Recommended)

Compile a Wallet example to `./tmp.sol`:

```console
java -ea -jar SCIF.jar -c test/contracts/ifcTypechecking/Wallet_lock_exception.scif -o ./tmp.sol
```

## Build from Source

Dependency:
* [JFlex](https://jflex.de/)
* Java 21
* Ant
* Gradle

```console
git clone --recurse-submodules https://github.com/apl-cornell/SCIF.git
cd SCIF
./gradlew build
./scif -c test/contracts/ifcTypechecking/Wallet_lock_exception.scif -o ./tmp.sol
```

## Documentation

The SCIF Reference Manual is published automatically at https://apl-cornell.github.io/SCIF/.

### Run locally

```console
yarn install
yarn docs:dev
```

