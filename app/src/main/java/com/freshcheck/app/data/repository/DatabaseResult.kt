package com.freshcheck.app.data.repository

enum class DatabaseError { InvalidInput, MissingRecord, InactiveFood, Conflict, Storage }

sealed interface DatabaseResult<out T> {
    data class Success<T>(val value: T) : DatabaseResult<T>
    data class Failure(val reason: DatabaseError, val message: String) : DatabaseResult<Nothing>
}
