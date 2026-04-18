# Types

SCIF is statically typed, meaning that each variable and method type needs to be specified at compile time. However, SCIF provides inference mechanisms to infer labels for programmer's convenience.

## Primitive types

* `bool`: Boolean values `true` or `false`.
* `uint`: Unsigned integers whose possible values are between 0 and 2<sup>256</sup> - 1.
* `bytes`: Byte array. 
* `address`: Address of an Ethereum account, represented as 20 bytes.

## Arrays

`T[n]` is the type of an array of element type `T` and fixed length `n`.
For example, `uint[10]` is the type of a `uint` array with 10 elements.
Indices are zero-based.

## Maps

`map(keyType, valueType)` represents a map from type `keyType` to type `valueType`.
For example, `map(address, uint)` maps from `address` to `uint`.

`keyType` can be any primitive type, while `valueType` can be any type,
including maps and user-defined classes.

The values in a map `m` with key `k` is accessed through the expression `m[k]`.

## Classes and Contracts

<!-- 
    TODO: add detailed explanations of classes.
 -->

Contract Types: Every defined contract can be explicitly converted from and to the `address` type.

## Labels

Each variable type in SCIF is associated with a label representing its level of integrity.
`T{l}` describes a type `T` associated with the label `l`.

For example:

```scif
uint{trusted} x;
uint{untrusted} y;
x = y; // compile error
y = x; // pass
```

`x` is labeled as `trusted` while `y` is labeled as `untrusted`. So when `x` is reassigned to `y`, the compiler will not compile because, assuming `trusted` and `untrusted` are defined in a reasonable way,
it is an integrity failure for an untrusted value to be assigned to a trusted variable.

If a label is not specified when declaring a variable, the compiler will either
infer a label from the context or assign a default label to it.
