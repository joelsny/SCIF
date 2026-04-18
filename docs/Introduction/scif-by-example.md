# SCIF by example

<!-- 
    Here gives some famous smart contract examples:
        audition
        wallet
        town crier
 -->

This section presents several small example contracts to illustrate core SCIF language features and common design patterns.

## Layout of a SCIF source file with `SimpleStorage`

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

### Importing from other source files

SCIF supports import statements. By using an import statement like the
following, all global symbols such as contracts are imported from the specified
file. 

```scif
import "path-to-file";
```

### Structure of a contract

The structure of SCIF contracts is similar to classes in object-oriented
programming languages such as Java. Each contract can declare
state variables, exceptions, constructors and
methods. In addition, a SCIF contract supports declarations of
events that help signaling important state changes of the contract.

#### Contract name, interface inheritance, and inheritance

Each contract starts with a name and optional inheritance declarations.

```scif
contract SimpleStorage implements IStorage extends BaseContract {
```

The contract declaration above specifies that `SimpleStorage` implements the `IStorage` interface and inherits from `BaseContract`.

#### State variables

State variables are variables that are stored persistently and used globally in the contract.

```scif
contract SimpleStorage {
    uint{this} storedData;
```

The above code declares an integer state variable, trusted by the owner. 

#### Exceptions

Exceptions can be used to indicate special behaviors and scenarios during contract executions.

```scif
    exception X();
    exception Y(uint aug1, bool aug2);
```

The above code declare two exceptions `X` and `Y`. Notice that exceptions can carry information as parameters.

#### Constructor methods

Constructor methods are optional. They provide convenience when creating a new contract and can help build invariants.

```scif
    constructor(...) {
        ...
    }
```

A constructor carries arbitrary parameters and can manipulate state variables.

#### Methods

Users can define public or private methods as they wish. In the example code, we define two methods `set` and `get`. They are declared in a way similar to Java and with additional information flow label annotations.

See the corresponding methods in chapter language basics for more information. 

## Multi-User Wallet

The following contract demonstrates a straightforward implementation of a multi-user wallet, which allows any user to deposit and withdraw funds from the wallet in a secure manner.

```scif
contract Wallet {
    map(address, uint) balances;
    
    constructor() {
    	super();
    }

    public void withdraw(uint amount) {
        endorse(amount, any -> this) if (balances[sender] >= amount) {
            lock(this) {
                send(sender, amount);
                balances[sender] -= amount;
            }
        } else {
            revert "insufficient funds";
        }
    }

    public payable void deposit() {
        balances[sender] += value;
    }
}
```

Both the `withdraw` and `deposit` methods are decorated as `public`, which designates them as *entry points* by default.
As a result, these methods can be invoked by anyone, including untrusted parties, to access the wallet's services.

The `withdraw` method is particularly noteworthy.
The sender initiates a withdrawal request for a specified amount of funds.
By default, the `amount` variable is labeled as `any` (indicating that it is untrusted).
However, to manipulate the sensitive `balances` data according to the untrusted `amount`, we need to employ a *conditioned endorsement*. 
This endorsement permits the endorsement of `amount` only when the sender possesses sufficient funds.

Within the `if` branch, the funds are transferred and the `balances` are updated.
Performing these operations without additional security measures could introduce a reentrancy vulnerability,
enabling the sender to call the `withdraw` method again before the `balances` have been updated.
To counter this, a dynamic lock is explicitly applied to the integrity level `this` (referring to the integrity level of the current contract instance). Consequently, SCIF ensures that no reentrancy can occur for any methods with integrity level `this`, including the `withdraw` method.

SCIF supports *exceptions* to enable more sophisticated control flows.
The following contract showcases a multi-user wallet implementation that incorporates exceptions.

```scif
contract Wallet {
    map(address, uint) balances;
    exception balanceNotEnough();
    exception transferFailure();
    
    constructor() {
    		super();
    }

    public void withdraw(uint amount) throws (balanceNotEnough, transferFailure) {
        endorse(amount, any -> this) when (balances[sender] >= amount) {
            lock(this) {
                atomic {
                    send(sender, amount);
                } rescue (error e) {
                    throw transferFailure();
                }
                balances[sender] -= amount;
            }
        } else {
            throw balanceNotEnough();
        }
    }

		public payable void deposit() {
        balances[sender] += value;
    }
}
```

In this example, two custom exceptions have been defined: `balanceNotEnough` and `transferFailure`.
These exceptions represent scenarios where the sender has insufficient funds and scenarios where the call to `send` fails, respectively.
It is important to note the use of the `atomic`/`rescue` pattern for invoking the `send` method and handling any exceptions or errors resulting from the call.
This pattern ensures that the control flow outcome of operations inside the `atomic` block does not influence the control flow outside the block, thus alleviating the burden on developers when reasoning about control flow.

## ERC20 token

[See Tutorial](https://apl-cornell.github.io/SCIF/Introduction/Your-First-SCIF-Contract.html)
