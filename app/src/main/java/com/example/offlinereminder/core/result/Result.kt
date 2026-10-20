package com.example.offlinereminder.core.result

/**
 * Functional outcome wrapper for domain operations and use cases.
 * Represents either a successful computation containing [data],
 * or a failure containing an [error] and optional [message].
 */
sealed interface Result<out T> {

    data class Success<out T>(val data: T) : Result<T>

    data class Failure(
        val error: Throwable,
        val message: String? = error.message
    ) : Result<Nothing>

    val isSuccess: Boolean
        get() = this is Success

    val isFailure: Boolean
        get() = this is Failure

    companion object {
        inline fun <T> runCatching(block: () -> T): Result<T> = try {
            Success(block())
        } catch (t: Throwable) {
            Failure(t)
        }
    }
}

fun <T> Result<T>.getOrNull(): T? = when (this) {
    is Result.Success -> data
    is Result.Failure -> null
}

fun <T> Result<T>.getOrDefault(defaultValue: T): T = when (this) {
    is Result.Success -> data
    is Result.Failure -> defaultValue
}

fun <T> Result<T>.getOrThrow(): T = when (this) {
    is Result.Success -> data
    is Result.Failure -> throw error
}

inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> = when (this) {
    is Result.Success -> Result.Success(transform(data))
    is Result.Failure -> this
}

inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
    is Result.Success -> transform(data)
    is Result.Failure -> this
}

inline fun <T> Result<T>.onSuccess(action: (T) -> Unit): Result<T> {
    if (this is Result.Success) action(data)
    return this
}

inline fun <T> Result<T>.onFailure(action: (Result.Failure) -> Unit): Result<T> {
    if (this is Result.Failure) action(this)
    return this
}

inline fun <T, R> Result<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (Result.Failure) -> R
): R = when (this) {
    is Result.Success -> onSuccess(data)
    is Result.Failure -> onFailure(this)
}
