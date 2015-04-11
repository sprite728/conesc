# Context #

## Enter in/Exit from Context ##

Basically, `Context` is an extension of module. In addition to the implementation of `layered function` it also can perform some actions on entering in and on exiting from this context. Another feature is function `check()` - depending on the boolean variable returned by this function, if `activate` key-word has been used, context will be active (if its `TRUE`) or not. It is useful when programmer wants to get additional information and then decide if it's feasible to activate current context or stay in previous context.

```
context DummyCon {
  uses interface SenderI as Sender;
} implementation {
  event void activated() {
    // actions on entering
    // init vars
  }
  event void deactivated() {
    // actions on exiting
    // clean
  }
  command boolean check(){
    // check data
    return true;
  }
}
```

Implementation of these functions is not mandatory, but programmer can use them to perform additional actions like variables initialisation on entering or cleaning on exiting, or to check additional data.

## Transitions ##

In context group can be more than two contexts. Let's consider we have `ActivityG` group with three contexts: `Running` (moving fast), `Moving` (moving slow) and `Resting` (not moving). From logical point of view, we can not move from `Running` to `Resting` context ignoring `Moving` context. To add this restriction we can use `transitions` key-word:

```
context Running {
  transitions Moving;
}implementation{
}
context Moving {
  transitions Running, Resting;
}implementation{
}
context Resting {
  transitions Moving;
}implementation{
}
```

## Triggers ##

There is also a way to _trigger_ another transition in another context group by using key-word `triggers`. Let's consider we have a `BatteryGroup` with two contexts `Low` and `Normal`. What we want to do is to disable any radio communications, until we gain enough battery power.

```
context Low {
  triggers BaseStationG.UnreachableCon;
}implementation{
}
```

Here we use `triggers` in order to activate `BaseStationG.UnreachableCon` as soon as `BatteryGroup.Low` context is activated. As we did [here](BasicsOfConesC.md) in `BaseStationG.UnreachableCon` function `send()` will not use radio module and will save reports locally. In general, after key-word `triggers` can be a list of contexts separated by comma.

## Dependencies ##

# Context Transitions Control #

Whenever the context transition initiated (whenever key-words `activate` or `triggers` are executed) ConesC performs a set of checks:

![https://dl.dropboxusercontent.com/s/uce6h0sun5xtgzy/activation_diagram.png](https://dl.dropboxusercontent.com/s/uce6h0sun5xtgzy/activation_diagram.png)

The first check in looks at feasible transitions. Feasible transitions are specified within the individual contexts using the keyword `transitions`. An attempt to initiate a transition from a context to one that is not explicitly listed in the former leads to the activation of the `Error` context. Indeed, such occurrences typically represent a significant design or implementation flaw requiring special handling at run-time, which programmers implement within
the `Error` context.

Within the `transitions` clause, the keyword `iff` is optionally employed to indicate the full name of another context whose activation is a requisite to perform the given transition. The second check verifies this rule, again leading to the `Error` context in case of violations, giving programmers a chance to handle the situation.

The last check considers violations to _soft_ requirements that do not necessarily indicate a design or implementation flaw. To implement such processing, ConesC programmers specify the proper conditions in the body of a predefined `check` command. If check returns false, the initiated context transition does not occur, and the system remains in the previous context.

## Error Context ##

`Error` context can be optionally specified by programmers to handle errors during the execution. If an error context is not declared, it is generated automatically. Programmer can define any context as `Error` context by adding `is error` modifier:

```
context group MyGroup{
...
}implementation{
  components
    MyFirstContext,
    MySecondContext is default,
    MyErrorContext is error;
...
}
```