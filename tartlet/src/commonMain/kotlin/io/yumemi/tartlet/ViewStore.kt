package io.yumemi.tartlet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.filter

/**
 * A view store that holds a snapshot of UI state and provides access to actions and events.
 *
 * ViewStore is an immutable container for the current UI state at a specific point in time.
 * It provides methods to execute actions on the underlying store, render UI based on specific
 * state types, and handle specific event types in Compose.
 *
 * ViewStore instances are typically created by [rememberViewStore], which creates a new
 * instance whenever the state changes to trigger Compose recomposition.
 *
 * For Compose previews, you can create a ViewStore with only state by omitting the [store]
 * parameter (which defaults to null):
 * ```
 * @Preview
 * @Composable
 * fun MyScreenPreview() {
 *     MyScreen(
 *         viewStore = ViewStore { MyState.Loading }
 *     )
 * }
 * ```
 *
 * @param S The type of UI state
 * @param E The type of UI event
 * @param ST The type of store, which must implement [Store]
 * @property store The store instance, nullable to support state-only ViewStores for previews
 * @param state A lambda that provides the current UI state snapshot
 */
@Suppress("unused")
@Stable
class ViewStore<S : Any, E : Any, ST : Store<*, *>>(
    @PublishedApi internal val store: ST? = null,
    state: () -> S,
) {
    val state: S = state()

    /**
     * Checks equality based on the current state.
     *
     * Two view stores are considered equal if they hold the same state.
     *
     * @param other The object to compare with
     * @return `true` if the states are equal, `false` otherwise
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ViewStore<*, *, *>) return false
        return this.state == other.state
    }

    /**
     * Returns the hash code based on the current state.
     *
     * @return The hash code of the state
     */
    override fun hashCode(): Int {
        return state.hashCode()
    }

    /**
     * Executes an action on the store.
     *
     * This method provides access to the underlying store instance within the receiver scope,
     * allowing you to call store methods that may update the state or emit events.
     * If the store is null (e.g., in preview mode), this method does nothing.
     *
     * Example: `viewStore.action { increment() }`
     *
     * @param block The action block to execute with the store as receiver
     */
    inline fun action(block: ST.() -> Unit) {
        store?.let(block)
    }

    /**
     * Renders UI for a specific state type.
     *
     * This method checks if the current state is of the specified type [S2] and
     * executes the render block if it matches. The ViewStore within the block is
     * automatically cast to `ViewStore<S2, E, *>`, narrowing the state type.
     *
     * This is useful for handling different state variants in a sealed interface hierarchy,
     * allowing type-safe access to specific state properties.
     *
     * @param S2 The specific state type to check for
     * @param block The render block to execute with a type-narrowed ViewStore
     */
    inline fun <reified S2 : S> render(block: ViewStore<S2, E, ST>.() -> Unit) {
        if (state is S2) {
            @Suppress("UNCHECKED_CAST")
            block(this as ViewStore<S2, E, ST>)
        }
    }

    /**
     * Handles specific event types in Compose.
     *
     * This composable function collects events from the store's event flow
     * and executes the provided block for events of type [E2]. The collection is
     * tied to the lifecycle of the composition and will be cancelled when the
     * composable leaves the composition.
     *
     * If the store is null (e.g., in preview mode), this method does nothing.
     *
     * @param E2 The specific event type to handle (can be a parent type to handle multiple events)
     * @param block The handler block to execute when an event of type [E2] is emitted
     */
    @Composable
    inline fun <reified E2 : E> handle(crossinline block: ViewStore<S, E, ST>.(E2) -> Unit) {
        LaunchedEffect(store) {
            store?.event?.filter { it is E2 }?.collect {
                block(this@ViewStore, it as E2)
            }
        }
    }
}

/**
 * Remembers a [ViewStore] instance in a Compose composition.
 *
 * This composable function creates and remembers a ViewStore that collects state from
 * the provided store. A new ViewStore instance is created whenever the state changes,
 * which triggers Compose recomposition due to ViewStore's equality check based on state.
 *
 * The underlying store instance is remembered across recompositions to maintain a stable
 * reference to the same store instance.
 *
 * @param S The type of UI state
 * @param E The type of UI event
 * @param ST The type of store, which must implement [Store]
 * @param store A lambda that provides the store to collect state and events from
 * @return A [ViewStore] instance that reflects the current state
 */
@Suppress("unused")
@Composable
fun <S : Any, E : Any, ST : Store<S, E>> rememberViewStore(store: @Composable () -> ST): ViewStore<S, E, ST> {
    // persist the initial Store instance across recompositions
    val holder = remember { object { var value: ST? = null } }
    val rememberedStore = holder.value ?: store().also { holder.value = it }

    val state by rememberedStore.state.collectAsState()

    return remember(state) {
        ViewStore(
            store = rememberedStore,
            state = { state },
        )
    }
}
