# Minecraft Query Language (mql)

A subset of MoLang (may eventually be a full implementation). Available as an interpreter or a JIT compiled mode.

## Background

## Install

Artifacts are published on Maven Central. Add the following to your `build.gradle(.kts)`:

```kotlin
dependencies {
    implementation("dev.hollowcube:mql:{VERSION}")
}
```

## Syntax

`mql` supports the following syntax

* Query functions
* Math & Comparison operators (`+`, `*`, `==`, etc)

## Usage

See the [docs](./docs/Basic%20Usage.md).

## Future Plans

* Unify the interpreter and compiler apis
    * Allows for fallback if using unsupported JIT features, permission issues, etc.
* Temp variables
* Public variables/querying other scripts
* Other data types & functions

## License

This project is licensed under the [MIT License](../../LICENSE).
