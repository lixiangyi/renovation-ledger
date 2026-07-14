package com.renovation.ledger.dsl

/**
 * Ternary-style conditional DSL from shared_uilib.
 *
 * Usage: `(x > 0) yes "pos" no "neg"`
 */
infix fun <T> Boolean.yes(valueIfTrue: T): YesBlock<T> = YesBlock(this, valueIfTrue)

class YesBlock<T>(
    @PublishedApi internal val condition: Boolean,
    @PublishedApi internal val valueIfTrue: T,
) {
    inline infix fun no(valueIfFalse: Supplier<T?>): T? {
        return if (condition) valueIfTrue else valueIfFalse()
    }

    infix fun no(valueIfFalse: T?): T? {
        return if (condition) valueIfTrue else valueIfFalse
    }

    inline infix fun nonNull(valueIfFalse: Supplier<T>): T {
        return if (condition) valueIfTrue else valueIfFalse()
    }

    infix fun nonNull(valueIfFalse: T): T {
        return if (condition) valueIfTrue else valueIfFalse
    }
}

inline infix fun <T> Boolean.yes(trueBlock: Supplier<T>): Conditional<T> {
    return Conditional({ this }, trueBlock())
}

inline infix fun <T> Conditional<T>.no(falseBlock: Supplier<T?>): T? = `else`(falseBlock)

inline infix fun <T> Conditional<T>.nonNull(falseBlock: Supplier<T>): T = notNull(falseBlock)

class Conditional<T>(
    @PublishedApi internal val condition: Supplier<Boolean>,
    @PublishedApi internal val valueIfTrue: T,
) {
    inline fun `else`(falseBlock: Supplier<T?>): T? {
        return if (condition()) valueIfTrue else falseBlock()
    }

    inline fun notNull(falseBlock: Supplier<T>): T {
        return if (condition()) valueIfTrue else falseBlock()
    }

    companion object {
        inline fun <T> of(condition: Boolean, trueBlock: Supplier<T>): Conditional<T> {
            return Conditional({ condition }, trueBlock())
        }
    }
}
