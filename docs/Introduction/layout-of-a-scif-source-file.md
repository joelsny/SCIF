# Layout of a SCIF source file

<!--
    Here describes the layout of the source file:
        imports
        the contract:
            name, ifc labels, inheritance
            variables
            event
            exceptions
            constructors
            methods
-->

A SCIF source file can contain multiple SCIF contract and interface
definitions. This section serves as a quick overview of the source file
structure.

Below is an example of a contract named `SimpleStorage`. It has a private integer (`uint`)
field named `storedData`, representing owner-trusted data as indicated by the label 
annotations `{this}` . The contract also defines some methods about the field. 
Importantly, the `set` method  that changes value of `storedData` is similarly labeled . 
Because `storedData` is labeled `{this}`, only sufficiently trusted contracts can call this 
method and make changes to the field. .

```scif
contract SimpleStorage {
  uint{this} storedData;
  
  exception X();
	exception Y(uint aug1, bool aug2);

	constructor() {
		super();
	}

	void set{this}(uint x) {
		storedData = x;
	}

	public uint get() {
		return storedData;
	}
}
```

## Importing from other source files

SCIF supports import statements. By using an import statement like the
following, all global symbols such as contracts are imported from the specified
file. 
```scif
import "path-to-file";
```

## Structure of a contract

The structure of SCIF contracts is similar to classes in object-oriented
programming languages such as Java. Each contract can declare
state variables, exceptions, constructors and
methods. In addition, a SCIF contract supports declarations of
events that help signaling important state changes of the contract.

### contract name, interface inheritance, and inheritance

Each contract starts with a name and optional inheritance declarations.

```scif
contract SimpleStorage implements IStorage extends BaseContract {
```

The contract declaration above specifies that `SimpleStorage` implements the `IStorage` interface and inherits from `BaseContract`.

### state variables

State variables are variables that are stored persistently and used globally in the contract.

```scif
contract SimpleStorage {
    uint{this} storedData;
```

The above code declares an integer state variable, trusted by the owner. 

### exceptions

Exceptions can be used to indicate special behaviors and scenarios during contract executions.

```scif
    exception X();
    exception Y(uint aug1, bool aug2);
```

The above code declare two exceptions `X` and `Y`. Notice that exceptions can carry information as parameters.

### constructor methods

Constructor methods are optional. They provide convenience when creating a new contract and can help build invariants.

```scif
    constructor(...) {
        ...
    }
```

A constructor carries arbitrary parameters and can manipulate state variables.

### methods

Users can define public or private methods as they wish. In the example code, we define two methods `set` and `get`. They are declared in a way similar to Java and with additional information flow label annotations.

See the corresponding methods in chapter language basics for more information. 
