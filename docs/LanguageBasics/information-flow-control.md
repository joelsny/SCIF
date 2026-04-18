# Information Flow Control

SCIF provides the ability to label information manipulated by programs with security policies. The compiler then enforces the security of the program by leveraging information flow control techniques.

## Principals

A principal is an entity that represents some power to change certain aspects of the program. In SCIF, a principal can either be an *address*, representing an on-chain account, or a unique identifier that are defined by the programmer and only carries meaning within the scope of the current contract, such as `{this}`, `{sender}`, `{any}`. 

## Trust relationships

In addtion to the set of principals, SCIF need to know the relationship between principals to detect information flow violations. The relationship between principals are defined by *flowsto* declarations.

For example, suppose principal `Alice` is trusted principal `Bob` in the program. It can be declared as:

```scif
Alice => Bob
```

It reads "Alice flows to Bob", meaning that Alice's information is allowed to flow to Bob.

If Alice and Bob are mutually trusted, the following expression captures such a relationship:

```scif
Alice == Bob
```

## Labels

In information flow control, integrity and confidentiality are well-known duals. In SCIF, we only focus on integrity because most smart contracts are currently running on public blockchains, where anyone can see all states of a smart contract.

In SCIF, an integrity policy is expressed as labels attached to code.

Here is a quick example:

```scif
final address owner;
uint{owner} trustedCounter;
```

`trustedCounter` is labeled as `owner`, meaning that the integrity level of `trustedCounter` is `owner`. In other words, only principals trusted by address `owner` can influence its value directly or indirectly.

A label can be as simple as a single principal. It can also be a complicated combination of multiple principals. See later sections for details.