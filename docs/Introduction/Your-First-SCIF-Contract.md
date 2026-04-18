# Getting Started with ERC20 Example

In this tutorial we will cover how to:

- Install the SCIF compiler and (optionally) the Foundry toolchain
- Explore the `IERC20` SCIF interface and its `ERC20` implementation
- Compile the `ERC20` SCIF code and (optionally) deploy it
- Extend the `ERC20` token with additional functionality

## Dependencies

Ensure the following are installed:

- Java 21
- Foundry (optional, for deployment) 

## Running SCIF Contracts

#### SCIF Compilation

Download the pre-built [SCIF JAR](https://github.com/apl-cornell/SCIF/releases/download/latest/SCIF.jar) and run:

```
java -ea -jar SCIF.jar -c [path_to_SCIF_contract]
```

::: info NOTE
Alternatively, to build SCIF from source, install Java 21, JFlex, Ant, and Gradle, then run:

```bash
git clone --recurse-submodules https://github.com/apl-cornell/SCIF.git
cd SCIF
./gradlew run --args [path_to_SCIF_contract]
```
:::

#### Deploying SCIF Contracts

After compilation, you will obtain a Solidity file that inherits the full security guarantees provided by SCIF: **no reentrancy vulnerabilities, no CDA vulnerabilities, and no improper error handling**. You can deploy it using the Foundry toolchain:

```bash
mkdir scif_erc20_token
cd scif_erc20_token
forge init --no-git
```

Copy the generated Solidity contracts into the `src` folder and run

```bash
forge build
```

Then deploy the contract to an Ethereum-compatible blockchain and interact with it by sending transactions or function calls:

```bash
forge create src/ERC20_depmap.sol:ERC20 \
  --rpc-url http://127.0.0.1:8545 \
  --private-key <PRIVATE_KEY> \
  --constructor-args "MyToken" "MTK" \
  --broadcast
```

## Understanding the ERC20 Contract in SCIF

Before diving into code, let's briefly review how SCIF's information flow control (IFC) works, since its annotations are what enable security guarantees.

### Basics on Information Flow Control (IFC)

SCIF labels information with security policies and uses compile-time IFC checks to ensure that sensitive data cannot be modified or influenced by untrusted sources.

#### Principal

A *principal* in SCIF represents an integrity source. It is typically a blockchain address or a symbol such as:

- `{this}` (the contract itself, highest integrity)
- `{sender}` (the caller)
- `{any}` (lowest integrity) 

#### Variable Labels

Each variable's integrity is declared with a label:

```
address{this} owner
```

This means only principals as trusted as `{this}` may influence `owner`. 

Inside a function, you may use `{sender}` as a label to indicate caller integrity. 

#### Function Labels

A function signature can also include optional labels to describe integrity requirements for the caller, parameters, return values and reentrancy locks. The general syntax looks like this:

```
bool{l_r} f{l_ex -> l_in; l_lk}(address{l_addr} addr, uint{l_amt} amt);
```

- `{l_ex}` - **External begin label (caller integrity)**

  This label specifies *who* is allowed to call the function.

  A function may only be invoked when the caller's integrity level is **at least as high as** `{l_ex}`. For instance, if `{l_ex}` is `from`, only a principal as trusted as `from` can invoke this function. If no external label is given, the default is `{sender}`, meaning no integrity restriction on callers.

- `{l_in}` - **Internal input label (auto-endorsement of control flow)**

  When the function body starts executing, SCIF *auto-endorses* the control flow from `l_ex` to `l_in`.

  This mechanism effectively raises the "trust level" of the ongoing computation. Auto-endorsement is what enables high-integrity operations (like modifying state protected by `this`) once the call passes its security check.

- `{l_out}` - **Reentrancy lock output label (reentrancy-free enforcement with input label)**

  This label specifies what integrity level are the renentrancy lock that is maintained by the function execution.

  To learn more about reentrancy and how SCIF prevents it, direct link to reentrancy section.

- `{l_addr}`, `{l_amt}` - **Parameter labels**

  Each parameter has its own integrity label indicating the minimum trust level required for its value.

  When the caller supplies arguments, those arguments must satisfy these label constraints. Typically, each parameter label is at least as restrictive as the external label `{l_ex}`, since untrusted parameters would violate the function’s integrity assumption.

- `{l_r}` - **Return-value label**

  The label attached to the return type indicates the integrity level of the result value the function produces.

  This ensures that results from low-integrity calls cannot later influence high-integrity logic without explicit endorsement.

When a label is omitted, SCIF applies the following safe defaults: 

```
l_ex    = this
l_in    = l_ex
l_out   = l_in
l_param = l_ex (for each parameter)
l_r     = l_ex
```

Thus, functions are callable only by trusted principals by default, and all inputs and outputs inherit the caller's integrity requirement. If a function is marked `public`, these defaults are overridden so that:

```
l_ex  = sender
l_in  = this
l_out = this
```

This allows external calls with immediate endorsement to the contract's integrity while maintaining reentrancy protection.

### `IERC20.scif` Interface

```scif
interface IERC20 {
    exception ERC20InsufficientBalance(address owner, uint cur, uint needed);
    exception ERC20InsufficientAllowance(address owner, uint cur, uint needed);
    public uint balanceOf(address account);
    public void approve{sender}(address allowed, uint amount);
    public void approveFrom{from}(final address from, address spender, uint val);
    public void transfer{from -> this}(final address from, address to, uint amount) throws (ERC20InsufficientBalance{this});
    public void transferFrom{sender -> from; sender}(final address from, address to, uint amount) throws (ERC20InsufficientAllowance{this}, ERC20InsufficientBalance{this});
}
```

`IERC20.scif` defines the `ERC20` token interface, including two exceptions and five public functions. A few things to note:

- `uint balanceOf(address account)` returns the balance of an account.

- `void approve{sender}(address allowed, uint amount)` allows the caller (`{sender}`) to set an allowance. 
- `void approveFrom{from}(final address from, address spender, uint val)` lets a caller that is as trusted as `{from}` to set an allowance on behalf of `from`.
- `void transfer{from -> this}(final address from, address to, uint amount)` moves tokens from `from` to `to` when the caller is as trusted as `{from}`, and the control flow will be auto-endorsed to the integrity level of `{this}` (which means the contract itself), enabling high-integrity operations.

### Implementing the `ERC20` Interface 

Now that we've examined the `IERC20` interface, let's look at how it is implemented in SCIF. The full [implementation](https://github.com/apl-cornell/SCIF/blob/master/test/contracts/tutorialExamples/ERC20.scif) is available. Below we walk through its structure step by step. 

#### Imports

```scif
import "./IERC20.scif";
```

The `import` statement includes other SCIF files by relative path. Here, we import the `IERC20` interface that we will implement. 

#### Contract Header and Inheritance

```scif
contract ERC20 implements IERC20 {
	...
}
```

As in Java, the header declares that `ERC20` implements the `IERC20` interface. Inheritance can also be specified if needed. 

#### State Variable Declarations

```scif
map(address, uint) _balances;
map(address owner, map(address, uint{owner}){owner}) _allowances;
uint _burnt;
uint _totalSupply;
bytes _name;
bytes _symbol;
```

State variables are persistent storage and are globally visible to all functions in the contract. SCIF supports common types such as `bool`, `uint`, `bytes`, `address`, `string`, fixed-length arrays `T[n]`, and associative maps `map(keyT, valT)`.  

Each type can carry an integrity label with the syntax `T{l}`, meaning the value of type `T` is labeled by `l`.

#### Exceptions

```scif
exception ERC20InsufficientBalance(address owner, uint cur, uint needed);
exception ERC20InsufficientAllowance(address owner, uint cur, uint needed);
```

Exceptions can be used to indicate special behaviors and scenarios during contract executions. Here, we declare two exceptions for handling insufficient balance and allowance scenarios during `ERC20` execution. Just like state varaibles and function definitions, exception declarations can be optionally annotated with information labels too.

#### Constructors

```scif
constructor(bytes name_, bytes symbol_) {
    _name = endorse(name_, sender -> this);
    _symbol = endorse(symbol_, sender -> this);
    super();
}
```

Contructors are optional; if present, the constructor will be executed once, just like in Solidity, when the contract is created. It is required to call `super()` before making any function calls in constructor. 

#### Functions

A typical function definition looks like this:

```scif
public void transfer{from -> this}(final address from, address to, uint val) 
		throws (ERC20InsufficientBalance{this}) 
{
    endorse([from, to, val], from -> this)
    when (_balances[from] >= val) {
        _balances[from] -= val;
        _balances[to] += val;
    } else {
        throw ERC20InsufficientBalance(from, _balances[from], val);
    }
}
```

This `ERC20` function transfer `val`-sized amount of tokens from `from` address to `to` address, potentially throwing `ERC20InsufficientBalance` exception.  

##### Function signature and labels

Function definitions are Java-like, with optional decorators placed at the start of the header: `public`, `private`, `payable`, and `override`. Like interfaces, function can have optional information labels annotated. 

In `ERC20.transfer` function, the external label `{l_ex}` is `from`, which is the first parameter address passed in by the caller; this means that the caller must have at least the same integrity level as principal `{from}` . The internal label is `{this}`, so the control flow is auto-endorsed to highest integrity level `{this}`, permitting the function body to perform high-integrity level operations. 

##### Function body and IFC statements

SCIF supports standard statements, including but not limited to assignment, contract creation, control flow expressions (`if`, `else`, `while`, `for`, `break`, `continue`, `return`), exception handling (`try`, `catch`), and function calls. There are also IFC-related statements, `endorse (var, low_lbl -> high_lbl) when (cond) { body_1 } else { body_2 }`, which will endorse `var`'s integrity from `{low_lbl}` to `{high_lbl}` and execute `body_1` only when `cond` is satisifed; otherwise, no endorsement happens and the program will execute `body_2`. 

The example `ERC20.transfer` function does the following step by step:

1. **Caller requirement**: the caller and all the parameters are required to be as trusted as principal `{from}`
2. **Auto-endorsement**: the control flow is auto-endorsed to the highest integrity level `{this}`
3. **Balance check**: verify whether address `from` has sufficient balance to send `val` tokens; if not, throw `ERC20InsufficientBalance` exception and exit the body
4. **Variable endorsement**: endorse all three parameters from `{from}` to the highest `{this}` integrity level
5. **State updates**: perform transactions to send `val` tokens from`from` address to `to` address

##### Why IFC is important

- If you downgrade the internal label in `ERC20.transfer`'s function signature to `{from}`, essentially voiding the second step above, the code will not compile. 
- If you remove the endorsement statement in the `transfer` function, essentially skipping the balance check in the third step, the code will also not compile. 

This examples shows how SCIF enforces the requirements and respects the principle that if the code compiles, it has "**no reentrancy vulnerabilities, no CDA vulnerabilities, and no improper error handling**".

## Extending the ERC20 Token

The provided `ERC20.scif` implements the core functionalities of the `ERC20` token, but you can easily extend it.

#### Adding Events

Like Solidity, SCIF supports event declarations for logging. Event declarations happen after exception definitions and before construtors. For example, we can add the following two events to the `ERC20`  contract. 

```scif
event Transfer(address from, address to, uint value);
event Approval(address owner, address spender, uint value);
```

You can emit them within functions, for example:

```scif
emit Transfer(from, to, val);
```

#### Minting and burning

Minting and burning are crucial for operating on `ERC20` tokens. Minting is used to create new tokens and add them to the system, and burning is for destroying tokens and removing them from the system. You can try to implement those with proper security annotations, and see if that compiles!

<details> <summary>Example implementation: mint & burn</summary>

```scif
/**
 * Only owners can mint tokens
 */
public void mint{this}(address to, uint val) {
    if (val > 0) {
        _totalSupply += val;
        _balances[to] += val;
    }
}

/**
 * Sender can only burn their own tokens
 */
public void burn{from -> this}(final address from, uint val)
    throws (ERC20InsufficientBalance{this})
{
    endorse([from, val], from -> this)
    when (_balances[from] >= val) {
        _balances[from] -= val;
        _totalSupply -= val;
        _burnt += val;
    } else {
        throw ERC20InsufficientBalance(from, _balances[from], val);
    }
}
```
</details>


<!--

## Further Reading
- Extending `IERC20` to Uniswap‑style interactions while preserving integrity
- Exceptions and error handling patterns (`try`/`catch`) in SCIF
- Reentrancy prevention
- CDA prevention 

-->