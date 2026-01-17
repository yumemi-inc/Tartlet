![logo](doc/logo.png)

[![Maven Central](https://img.shields.io/maven-central/v/io.yumemi/tartlet)](https://central.sonatype.com/artifact/io.yumemi/tartlet)
![License](https://img.shields.io/github/license/yumemi-inc/Tartlet)
[![Java CI with Gradle](https://github.com/yumemi-inc/Tartlet/actions/workflows/gradle.yml/badge.svg)](https://github.com/yumemi-inc/Tartlet/actions/workflows/gradle.yml)

Tartlet is a helper library for Compose Multiplatform.

Key benefits:
- **Eliminate callback hoisting**: Pass state and actions to child Composables, eliminating the need to hoist click events and other callbacks to parent Composables
- **Simplified preview development**: Develop UI with Android Studio previews by creating instances with only state, without requiring ViewModels

## Table of Contents

- [Installation](#installation)
- [Basic usage](#basic-usage)
  - [Define state](#define-state)
  - [Define event](#define-event)
  - [Store](#store)
  - [ViewStore](#viewstore)
- [Passing ViewStore to child Composables](#passing-viewstore-to-child-composables)
- [Cases where there are no events to handle](#cases-where-there-are-no-events-to-handle)
- [Rendering multiple states](#rendering-multiple-states)
- [Handling multiple events](#handling-multiple-events)
- [Mock for previewing in Android Studio](#mock-for-previewing-in-android-studio)

## Installation

```kt
implementation("io.yumemi:tartlet:<latest-release>")
```

## Basic usage

### Define state

Define a data class to represent your UI state:

```kotlin
data class CounterState(val count: Int)
```

### Define event

Define a sealed interface for one-time UI events:

```kotlin
sealed interface CounterEvent {
    data class ShowToast(val message: String) : CounterEvent
}
```

### Store

Typically implemented by a `ViewModel`:

```kotlin
class CounterViewModel : ViewModel(), Store<CounterState, CounterEvent> { // Inherits Store
    private val _state = MutableStateFlow<CounterState>(CounterState(count = 0))
    override val state = _state.asStateFlow() // Override state property

    private val _event = MutableSharedFlow<CounterEvent>()
    override val event = _event.asSharedFlow() // Override event property

    fun increment() {
        _state.update { it.copy(count = it.count + 1) }
    }

    fun decrement() {
        if (0 < _state.value.count) {
            _state.update { it.copy(count = it.count - 1) }
        } else {
            viewModelScope.launch { _event.emit(CounterEvent.ShowToast("Cannot decrement below zero.")) }
        }
    }
}
```

> **Note**: Tartlet itself does not have a state persistence feature. To maintain the screen state, the `ViewModel` must serve as the `Store`.

### ViewStore

An immutable snapshot of UI state that provides methods to render state values, execute actions, and handle events:

```kotlin
@Composable
fun CounterScreen(
    viewStore: ViewStore<CounterState, CounterEvent, CounterViewModel> = rememberViewStore { viewModel() },
) {
    Column {
        Text("Count: ${viewStore.state.count}")

        // Call ViewModel method
        Button(onClick = { viewStore.action { increment() } }) { 
            Text("Increment")
        }
        Button(onClick = { viewStore.action { decrement() } }) {
            Text("Decrement")
        }
    }

    viewStore.handle<CounterEvent.ShowToast> { event ->
        // Show toast..
    }
}
```

## Passing ViewStore to child Composables

Passing ViewStore instances to child Composables eliminates the need to hoist actions:

```kotlin
@Composable
fun CounterScreen(
    viewStore: ViewStore<CounterState, CounterEvent, CounterViewModel> = rememberViewStore { viewModel() },
) {
    CounterContent(viewStore = viewStore) // Pass ViewStore to child

    viewStore.handle<CounterEvent.ShowToast> { event ->
        // Show toast..
    }
}

@Composable
private fun CounterContent(
    viewStore: ViewStore<CounterState, CounterEvent, CounterViewModel>
) {
    Column {
        Text("Count: ${viewStore.state.count}")

        // No need to hoist actions
        Button(onClick = { viewStore.action { increment() } }) { 
            Text("Increment")
        }
        Button(onClick = { viewStore.action { decrement() } }) {
            Text("Decrement")
        }
    }
}
```

## Cases where there are no events to handle

Specify `Nothing` for the event type.

```kt
class CounterViewModel : ViewModel(), Store<CounterState, Nothing> {
    private val _state = MutableStateFlow<CounterState>(CounterState(count = 0))
    override val state = _state.asStateFlow()

    // No need to override event property when using Nothing type

    fun increment() { ... }
    fun decrement() { ... }
}
```

## Rendering multiple states

When using sealed interfaces for multiple states, use `ViewStore.render()` to render different UI based on the current state type:

```kotlin
sealed interface CounterState {
    data object Loading : CounterState
    data class Stable(val count: Int) : CounterState
    data class Error(val message: String) : CounterState
}

@Composable
fun CounterScreen(
    viewStore: ViewStore<CounterState, Nothing, CounterViewModel> = rememberViewStore { viewModel() },
) {
    viewStore.render<CounterState.Loading> {
        CircularProgressIndicator()
    }

    viewStore.render<CounterState.Stable> {
        Column {
            Text("Count: ${state.count}") // state is cast to CounterState.Stable

            Button(onClick = { action { increment() } }) {
                Text("Increment")
            }
            Button(onClick = { action { decrement() } }) {
                Text("Decrement")
            }
        }
    }

    viewStore.render<CounterState.Error> {
        Text("Error: ${state.message}", color = Color.Red) // state is cast to CounterState.Error
    }
}
```

The ViewStore's state type is automatically narrowed within the render block, allowing the casted ViewStore to be passed to child Composables:

```kt
@Composable
fun CounterScreen(
    viewStore: ViewStore<CounterState, Nothing, CounterViewModel> = rememberViewStore { viewModel() },
) {
    viewStore.render<CounterState.Loading> {
        // ...
    }

    viewStore.render<CounterState.Stable> {
        CounterContent(viewStore = this) // Pass casted ViewStore to child
    }

    viewStore.render<CounterState.Error> {
        // ...
    }
}

@Composable
private fun CounterContent(
    viewStore: ViewStore<CounterState.Stable, Nothing, CounterViewModel> // state is cast to CounterState.Stable
) {
    Column {
        Text("Count: ${viewStore.state.count}")

        // ...
    }
}
```

## Handling multiple events

Handle the parent event type and use `when` expressions to process each event type:

```kt
sealed interface CounterEvent {
    data class ShowToast(val message: String) : CounterEvent
    data class NavigateToDetail(val id: Int) : CounterEvent
    data object Refresh : CounterEvent
}

@Composable
fun CounterScreen(
    viewStore: ViewStore<CounterState, CounterEvent, CounterViewModel> = rememberViewStore { viewModel() },
) {
    // ...

    viewStore.handle<CounterEvent> { event ->
        when (event) {
            is CounterEvent.ShowToast -> {
                // Show toast with event.message
            }
            is CounterEvent.NavigateToDetail -> {
                // Navigate to detail screen with event.id
            }
            is CounterEvent.Refresh -> {
                // Refresh the screen
            }
        }
    }
}
```

## Mock for previewing in Android Studio

Create an instance of `ViewStore` directly with the target state.

```kt
@Preview
@Composable
fun CounterScreenLoadingPreview() {
    MyApplicationTheme {
        CounterScreen(
            viewStore = ViewStore {
                CounterState.Loading
            },
        )
    }
}
```

> **Tips**: This can also be used to mock dependencies in unit tests for composables.

This allows UI development with only state, without requiring a ViewModel.
