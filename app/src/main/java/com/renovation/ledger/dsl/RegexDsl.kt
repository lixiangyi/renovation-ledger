package com.renovation.ledger.dsl

import java.util.regex.Pattern

/**
 * Grammar-style regex builder from shared_uilib (demo/main stripped).
 */
interface IRegexTextBuilder {
    fun anyChar(): IRegexTextBuilder
    fun digit(): IRegexTextBuilder
    fun nonDigit(): IRegexTextBuilder
    fun whiteSpace(): IRegexTextBuilder
    fun nonWhiteSpace(): IRegexTextBuilder
    fun letter(): IRegexTextBuilder
    fun alphaNumeric(): IRegexTextBuilder
    fun whitespace(): IRegexTextBuilder
    fun wordBoundary(): IRegexTextBuilder
    fun nonWordBoundary(): IRegexTextBuilder
    fun blankLine(): IRegexTextBuilder
    fun wordCharacter(): IRegexTextBuilder
    fun nonWordCharacter(): IRegexTextBuilder
    fun startOfLine(): IRegexTextBuilder
    fun endOfLine(): IRegexTextBuilder
    fun literally(s: String): IRegexTextBuilder
    fun oneOrMore(block: VoidCallbackWithReceiver<RegexContext>): IRegexTextBuilder
    fun oneOrMoreWithLazyMatch(block: VoidCallbackWithReceiver<RegexContext>): IRegexTextBuilder
    fun oneOrMore(s: String): IRegexTextBuilder
    fun oneOrMoreWithLazyMatch(s: String): IRegexTextBuilder
    fun zeroOrMore(block: VoidCallbackWithReceiver<RegexContext>): IRegexTextBuilder
    fun zeroOrMoreWithLazyMatch(block: VoidCallbackWithReceiver<RegexContext>): IRegexTextBuilder
    fun zeroOrMore(s: String): IRegexTextBuilder
    fun zeroOrMoreWithLazyMatch(s: String): IRegexTextBuilder
    fun optional(block: VoidCallbackWithReceiver<RegexContext>): IRegexTextBuilder
    fun optional(s: String): IRegexTextBuilder
    infix fun Int.times(block: VoidCallbackWithReceiver<RegexContext>)
    infix fun Int.times(s: String)
    infix fun IntRange.times(block: VoidCallbackWithReceiver<RegexContext>)
    infix fun IntRange.timesWithLazyMatch(block: VoidCallbackWithReceiver<RegexContext>)
    infix fun IntRange.times(s: String)
    infix fun IntRange.timesWithLazyMatch(s: String)
    infix fun Int.timesOrMore(block: VoidCallbackWithReceiver<RegexContext>)
    infix fun Int.timesOrMoreWithLazyMatch(block: VoidCallbackWithReceiver<RegexContext>)
    infix fun Int.timesOrMore(s: String)
    infix fun Int.timesOrMoreWithLazyMatch(s: String)
    infix fun Int.timesOrLess(block: VoidCallbackWithReceiver<RegexContext>)
    infix fun Int.timesOrLessWithLazyMatch(block: VoidCallbackWithReceiver<RegexContext>)
    infix fun Int.timesOrLess(s: String)
    infix fun Int.timesOrLessWithLazyMatch(s: String)
    fun group(block: VoidCallbackWithReceiver<RegexContext>): Int
    fun group(s: String): Int
    fun group(name: String, block: VoidCallbackWithReceiver<RegexContext>): Int
    fun group(name: String, s: String): Int
    fun matchGroup(index: Int): IRegexTextBuilder
    fun matchGroup(name: String): IRegexTextBuilder
    fun include(regex: Regex): IRegexTextBuilder
    fun or(vararg conditions: String): IRegexTextBuilder
    fun or(vararg blocks: VoidCallbackWithReceiver<RegexContext>): IRegexTextBuilder
    fun oneOf(vararg characters: Char): IRegexTextBuilder
    fun noneOf(vararg characters: Char): IRegexTextBuilder
    fun oneOf(vararg ranges: CharRange): IRegexTextBuilder
    fun noneOf(vararg ranges: CharRange): IRegexTextBuilder
    fun forwardPositiveAssert(block: VoidCallbackWithReceiver<RegexContext>): IRegexTextBuilder
    fun forwardPositiveAssert(s: String): IRegexTextBuilder
    fun forwardNegativeAssert(block: VoidCallbackWithReceiver<RegexContext>): IRegexTextBuilder
    fun forwardNegativeAssert(s: String): IRegexTextBuilder
    fun backwardPositiveAssert(block: VoidCallbackWithReceiver<RegexContext>): IRegexTextBuilder
    fun backwardPositiveAssert(s: String): IRegexTextBuilder
    fun backwardNegativeAssert(block: VoidCallbackWithReceiver<RegexContext>): IRegexTextBuilder
    fun backwardNegativeAssert(s: String): IRegexTextBuilder
    fun build(): String
}

class RegexContext(@PublishedApi internal var lastGroup: Int = 0) : IRegexTextBuilder {
    private val regexParts = StringBuilder()

    @PublishedApi
    internal fun addPart(part: String) {
        regexParts.append(part)
    }

    override fun anyChar(): IRegexTextBuilder {
        addPart(MetaCharacter.ANY_CHAR)
        return this
    }

    override fun digit(): IRegexTextBuilder {
        addPart(MetaCharacter.DIGIT)
        return this
    }

    override fun nonDigit(): IRegexTextBuilder {
        addPart(MetaCharacter.NON_DIGIT)
        return this
    }

    override fun whiteSpace(): IRegexTextBuilder {
        addPart(MetaCharacter.WHITE_SPACE)
        return this
    }

    override fun nonWhiteSpace(): IRegexTextBuilder {
        addPart(MetaCharacter.NON_WHITE_SPACE)
        return this
    }

    override fun letter(): IRegexTextBuilder {
        addPart(MetaCharacter.LETTER)
        return this
    }

    override fun alphaNumeric(): IRegexTextBuilder {
        addPart(MetaCharacter.ALPHA_NUMERIC)
        return this
    }

    override fun whitespace(): IRegexTextBuilder {
        addPart(MetaCharacter.WHITE_SPACE)
        return this
    }

    override fun wordBoundary(): IRegexTextBuilder {
        addPart(MetaCharacter.WORD_BOUNDARY)
        return this
    }

    override fun nonWordBoundary(): IRegexTextBuilder {
        addPart(MetaCharacter.NON_WORD_BOUNDARY)
        return this
    }

    override fun blankLine(): IRegexTextBuilder {
        addPart(MetaCharacter.BLANK_LINE)
        return this
    }

    override fun wordCharacter(): IRegexTextBuilder {
        addPart(MetaCharacter.WORD_CHARACTER)
        return this
    }

    override fun nonWordCharacter(): IRegexTextBuilder {
        addPart(MetaCharacter.NON_WORD_CHARACTER)
        return this
    }

    override fun startOfLine(): IRegexTextBuilder {
        addPart(MetaCharacter.START_OF_LINE)
        return this
    }

    override fun endOfLine(): IRegexTextBuilder {
        addPart(MetaCharacter.END_OF_LINE)
        return this
    }

    override fun literally(s: String): IRegexTextBuilder {
        addPart(Regex.escape(s))
        return this
    }

    @PublishedApi
    internal inline fun appendQuantifiersModifier(
        s: String,
        enableLazyMatch: Boolean,
        modifier: QuantifierDef.() -> String,
    ) = addPart("(?:$s)${modifier(QuantifierDef)}${enableLazyMatch yes "?" nonNull ""}")

    @Suppress("unused")
    inline fun appendQuantifiersModifier(
        character: Char,
        modifier: QuantifierDef.() -> String,
    ) = addPart("$character${modifier(QuantifierDef)}")

    @Suppress("unused")
    inline fun appendQuantifiersModifierWithLazyMatch(
        character: Char,
        modifier: QuantifierDef.() -> String,
    ) = addPart("$character${modifier(QuantifierDef)}?")

    @PublishedApi
    internal inline fun pattern(block: VoidCallbackWithReceiver<RegexContext>): String {
        val regexContext = RegexContext(lastGroup).apply(block)
        lastGroup = regexContext.lastGroup
        return regexContext.build()
    }

    override inline fun oneOrMoreWithLazyMatch(block: VoidCallbackWithReceiver<RegexContext>): IRegexTextBuilder {
        appendQuantifiersModifier(pattern(block), true) { ONE_OR_MORE }
        return this
    }

    override inline fun oneOrMore(block: VoidCallbackWithReceiver<RegexContext>): IRegexTextBuilder {
        appendQuantifiersModifier(pattern(block), false) { ONE_OR_MORE }
        return this
    }

    override fun oneOrMoreWithLazyMatch(s: String) = oneOrMoreWithLazyMatch { literally(s) }

    override fun oneOrMore(s: String) = oneOrMore { literally(s) }

    override inline fun optional(block: VoidCallbackWithReceiver<RegexContext>): IRegexTextBuilder {
        appendQuantifiersModifier(pattern(block), false) { OPTIONAL }
        return this
    }

    override fun optional(s: String) = optional { literally(s) }

    override inline fun zeroOrMoreWithLazyMatch(block: VoidCallbackWithReceiver<RegexContext>): IRegexTextBuilder {
        appendQuantifiersModifier(pattern(block), true) { ZERO_OR_MORE }
        return this
    }

    override inline fun zeroOrMore(block: VoidCallbackWithReceiver<RegexContext>): IRegexTextBuilder {
        appendQuantifiersModifier(pattern(block), false) { ZERO_OR_MORE }
        return this
    }

    override fun zeroOrMoreWithLazyMatch(s: String) = zeroOrMoreWithLazyMatch { literally(s) }

    override fun zeroOrMore(s: String) = zeroOrMore { literally(s) }

    override inline infix fun Int.times(block: VoidCallbackWithReceiver<RegexContext>) =
        appendQuantifiersModifier(pattern(block), false) { this@times.times }

    override infix fun Int.times(s: String) = this times { literally(s) }

    override inline infix fun IntRange.timesWithLazyMatch(block: VoidCallbackWithReceiver<RegexContext>) =
        appendQuantifiersModifier(pattern(block), true) { this@timesWithLazyMatch.times }

    override inline infix fun IntRange.times(block: VoidCallbackWithReceiver<RegexContext>) =
        appendQuantifiersModifier(pattern(block), false) { this@times.times }

    override infix fun IntRange.timesWithLazyMatch(s: String) = this timesWithLazyMatch { literally(s) }

    override infix fun IntRange.times(s: String) = this times { literally(s) }

    override inline infix fun Int.timesOrMoreWithLazyMatch(block: VoidCallbackWithReceiver<RegexContext>) =
        appendQuantifiersModifier(pattern(block), true) { this@timesOrMoreWithLazyMatch.timesOrMore }

    override inline infix fun Int.timesOrMore(block: VoidCallbackWithReceiver<RegexContext>) =
        appendQuantifiersModifier(pattern(block), false) { this@timesOrMore.timesOrMore }

    override infix fun Int.timesOrMoreWithLazyMatch(s: String) =
        this timesOrMoreWithLazyMatch { literally(s) }

    override infix fun Int.timesOrMore(s: String) = this timesOrMore { literally(s) }

    override inline infix fun Int.timesOrLessWithLazyMatch(block: VoidCallbackWithReceiver<RegexContext>) =
        appendQuantifiersModifier(pattern(block), true) { this@timesOrLessWithLazyMatch.timesOrLess }

    override inline infix fun Int.timesOrLess(block: VoidCallbackWithReceiver<RegexContext>) =
        appendQuantifiersModifier(pattern(block), false) { this@timesOrLess.timesOrLess }

    override infix fun Int.timesOrLessWithLazyMatch(s: String) =
        this timesOrLessWithLazyMatch { literally(s) }

    override infix fun Int.timesOrLess(s: String) = this timesOrLess { literally(s) }

    override inline fun group(block: VoidCallbackWithReceiver<RegexContext>): Int {
        val res = ++lastGroup
        addPart("(${pattern(block)})")
        return res
    }

    override fun group(s: String) = group { literally(s) }

    override inline fun group(groupName: String, block: VoidCallbackWithReceiver<RegexContext>): Int {
        val res = ++lastGroup
        addPart("(?<$groupName>${pattern(block)})")
        return res
    }

    override fun group(name: String, s: String) = group(name) { literally(s) }

    override fun matchGroup(groupIndex: Int): IRegexTextBuilder {
        addPart("\\$groupIndex")
        return this
    }

    override fun matchGroup(groupName: String): IRegexTextBuilder {
        addPart("\\k<$groupName>")
        return this
    }

    override fun include(regex: Regex): IRegexTextBuilder {
        val pattern = regex.pattern
        addPart(pattern)
        lastGroup += Pattern.compile(pattern).matcher("").groupCount()
        return this
    }

    override fun or(vararg conditions: String): IRegexTextBuilder {
        addPart(conditions.joinToString("|", "(?:", ")") { Regex.escape(it) })
        return this
    }

    override inline fun or(vararg blocks: VoidCallbackWithReceiver<RegexContext>): IRegexTextBuilder {
        addPart(blocks.joinToString("|", "(?:", ")") { pattern(it) })
        return this
    }

    override fun oneOf(vararg characters: Char): IRegexTextBuilder {
        addPart(characters.joinToString("", "[", "]").replace("\\", "\\\\").replace("^", "\\^"))
        return this
    }

    override fun noneOf(vararg characters: Char): IRegexTextBuilder {
        addPart(characters.joinToString("", "[^", "]").replace("\\", "\\\\"))
        return this
    }

    override fun oneOf(vararg ranges: CharRange): IRegexTextBuilder {
        addPart(ranges.joinToString("", "[", "]") { "${it.first}-${it.last}" })
        return this
    }

    override fun noneOf(vararg ranges: CharRange): IRegexTextBuilder {
        addPart(ranges.joinToString("", "[^", "]") { "${it.first}-${it.last}" })
        return this
    }

    override inline fun forwardPositiveAssert(block: VoidCallbackWithReceiver<RegexContext>): IRegexTextBuilder {
        addPart("(?=${pattern(block)})")
        return this
    }

    override fun forwardPositiveAssert(s: String): IRegexTextBuilder =
        forwardPositiveAssert { literally(s) }

    override inline fun forwardNegativeAssert(block: VoidCallbackWithReceiver<RegexContext>): IRegexTextBuilder {
        addPart("(?!${pattern(block)})")
        return this
    }

    override fun forwardNegativeAssert(s: String): IRegexTextBuilder =
        forwardNegativeAssert { literally(s) }

    override inline fun backwardPositiveAssert(block: VoidCallbackWithReceiver<RegexContext>): IRegexTextBuilder {
        addPart("(?<=${pattern(block)})")
        return this
    }

    override fun backwardPositiveAssert(s: String): IRegexTextBuilder =
        backwardPositiveAssert { literally(s) }

    override inline fun backwardNegativeAssert(block: VoidCallbackWithReceiver<RegexContext>): IRegexTextBuilder {
        addPart("(?<!${pattern(block)})")
        return this
    }

    override fun backwardNegativeAssert(s: String): IRegexTextBuilder =
        backwardNegativeAssert { literally(s) }

    override fun build() = regexParts.toString()
}

inline fun regex(block: VoidCallbackWithReceiver<RegexContext>): Regex {
    val regexContext = RegexContext().apply(block)
    return Regex(regexContext.build())
}

@Suppress("unused")
object QuantifierDef {
    const val OPTIONAL = "?"
    const val ONE_OR_MORE = "+"
    const val ZERO_OR_MORE = "*"

    inline val Int.times: String
        get() = "{$this}"

    inline val IntRange.times: String
        get() = "{$first,$last}"

    inline val Int.timesOrMore: String
        get() = "{$this,}"

    inline val Int.timesOrLess: String
        get() = "{0,$this}"
}

@Suppress("unused")
object MetaCharacter {
    const val ANY_CHAR = "."
    const val DIGIT = "\\d"
    const val NON_DIGIT = "\\D"
    const val WORD_CHARACTER = "\\w"
    const val NON_WORD_CHARACTER = "\\W"
    const val WORD_BOUNDARY = "\\b"
    const val NON_WORD_BOUNDARY = "\\B"
    const val WHITE_SPACE = "\\s"
    const val NON_WHITE_SPACE = "\\S"
    const val BLANK_LINE = "^$"
    const val LETTER = "[a-zA-Z]"
    const val ALPHA_NUMERIC = "[a-zA-Z0-9]"
    const val START_OF_LINE = "^"
    const val END_OF_LINE = "$"
}
