package core.functionality

@FunctionalInterface
interface Filter<T> {
    fun filter(obj: T)
}