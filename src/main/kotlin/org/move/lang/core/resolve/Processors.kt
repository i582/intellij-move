package org.move.lang.core.resolve

import com.intellij.util.SmartList
import org.move.lang.core.psi.MvElement
import org.move.lang.core.psi.MvModule
import org.move.lang.core.psi.MvNamedElement
import org.move.lang.core.psi.MvPath
import org.move.lang.core.resolve.ref.Namespace
import org.move.lang.core.resolve2.ref.PathResolutionContext
import org.move.lang.core.resolve2.ref.RsPathResolveResult
import org.move.stdext.intersects

/**
 * ScopeEntry is some PsiElement visible in some code scope.
 *
 * [SimpleScopeEntry] handles the two cases:
 *   * aliases (that's why we need a [name] property)
 *   * lazy resolving of actual elements (that's why [element] can return `null`) - unused for now
 */
interface ScopeEntry {
    val name: String
    val element: MvElement
    val namespaces: Set<Namespace>
    fun doCopyWithNs(namespaces: Set<Namespace>): ScopeEntry
}

@Suppress("UNCHECKED_CAST")
private fun <T: ScopeEntry> T.copyWithNs(namespaces: Set<Namespace>): T = doCopyWithNs(namespaces) as T

typealias RsProcessor<T> = (T) -> Boolean

interface RsResolveProcessorBase<in T: ScopeEntry> {
    /**
     * Return `true` to stop further processing,
     * return `false` to continue search
     */
    fun process(entry: T): Boolean

    /**
     * Indicates that processor is interested only in [ScopeEntry]s with specified [names].
     * Improves performance for Resolve2.
     * `null` in completion
     */
    val names: Set<String>?

    fun acceptsName(name: String): Boolean {
        val names = names
        return names == null || name in names
    }
}

//interface RsResolveProcessor {
//    /**
//     * Return `true` to stop further processing,
//     * return `false` to continue search
//     */
//    fun process(entry: SimpleScopeEntry): Boolean
//
//    /**
//     * Indicates that processor is interested only in [SimpleScopeEntry]s with specified [names].
//     * Improves performance for Resolve2.
//     * `null` in completion
//     */
//    val names: Set<String>?
//
//    fun acceptsName(name: String): Boolean {
//        val names = names
//        return names == null || name in names
//    }
//}

typealias RsResolveProcessor = RsResolveProcessorBase<ScopeEntry>

fun createStoppableProcessor(processor: (ScopeEntry) -> Boolean): RsResolveProcessor {
    return object: RsResolveProcessorBase<ScopeEntry> {
        override fun process(entry: ScopeEntry): Boolean = processor(entry)
        override val names: Set<String>? get() = null
    }
}

fun createProcessor(processor: (ScopeEntry) -> Unit): RsResolveProcessor {
    return object: RsResolveProcessorBase<ScopeEntry> {
        override fun process(entry: ScopeEntry): Boolean {
            processor(entry)
            return false
        }

        override val names: Set<String>? get() = null
    }
}

fun <T: ScopeEntry, U: ScopeEntry> RsResolveProcessorBase<T>.wrapWithMapper(
    mapper: (U) -> T
): RsResolveProcessorBase<U> {
    return MappingProcessor(this, mapper)
}

private class MappingProcessor<in T: ScopeEntry, in U: ScopeEntry>(
    private val originalProcessor: RsResolveProcessorBase<T>,
    private val mapper: (U) -> T,
): RsResolveProcessorBase<U> {
    override val names: Set<String>? = originalProcessor.names
    override fun process(entry: U): Boolean {
        val mapped = mapper(entry)
        return originalProcessor.process(mapped)
    }

    override fun toString(): String = "MappingProcessor($originalProcessor, mapper = $mapper)"
}

fun <T: ScopeEntry, U: ScopeEntry> RsResolveProcessorBase<T>.wrapWithNonNullMapper(
    mapper: (U) -> T?
): RsResolveProcessorBase<U> {
    return NonNullMappingProcessor(this, mapper)
}

private class NonNullMappingProcessor<in T: ScopeEntry, in U: ScopeEntry>(
    private val originalProcessor: RsResolveProcessorBase<T>,
    private val mapper: (U) -> T?,
): RsResolveProcessorBase<U> {
    override val names: Set<String>? = originalProcessor.names
    override fun process(entry: U): Boolean {
        val mapped = mapper(entry)
        return if (mapped == null) {
            false
        } else {
            originalProcessor.process(mapped)
        }
    }

    override fun toString(): String = "MappingProcessor($originalProcessor, mapper = $mapper)"
}

fun <T: ScopeEntry> RsResolveProcessorBase<T>.wrapWithFilter(
    filter: (T) -> Boolean
): RsResolveProcessorBase<T> {
    return FilteringProcessor(this, filter)
}

private class FilteringProcessor<in T: ScopeEntry>(
    private val originalProcessor: RsResolveProcessorBase<T>,
    private val filter: (T) -> Boolean,
): RsResolveProcessorBase<T> {
    override val names: Set<String>? = originalProcessor.names
    override fun process(entry: T): Boolean {
        return if (filter(entry)) {
            originalProcessor.process(entry)
        } else {
            false
        }
    }

    override fun toString(): String = "FilteringProcessor($originalProcessor, filter = $filter)"
}

fun <T: ScopeEntry> RsResolveProcessorBase<T>.wrapWithBeforeProcessingHandler(
    handler: (T) -> Unit
): RsResolveProcessorBase<T> {
    return BeforeProcessingProcessor(this, handler)
}

private class BeforeProcessingProcessor<in T: ScopeEntry>(
    private val originalProcessor: RsResolveProcessorBase<T>,
    private val handler: (T) -> Unit,
): RsResolveProcessorBase<T> {
    override val names: Set<String>? = originalProcessor.names
    override fun process(entry: T): Boolean {
        handler(entry)
        return originalProcessor.process(entry)
    }

    override fun toString(): String = "BeforeProcessingProcessor($originalProcessor, handler = $handler)"
}


fun <T : ScopeEntry> RsResolveProcessorBase<T>.wrapWithShadowingProcessor(
    prevScope: Map<String, Set<Namespace>>,
    ns: Set<Namespace>,
): RsResolveProcessorBase<T> {
    return ShadowingProcessor(this, prevScope, ns)
}

private class ShadowingProcessor<in T : ScopeEntry>(
    private val originalProcessor: RsResolveProcessorBase<T>,
    private val prevScope: Map<String, Set<Namespace>>,
    private val ns: Set<Namespace>,
) : RsResolveProcessorBase<T> {
    override val names: Set<String>? = originalProcessor.names
    override fun process(entry: T): Boolean {
        val prevNs = prevScope[entry.name]
        if (entry.name == "_" || prevNs == null) return originalProcessor.process(entry)
        val restNs = entry.namespaces.minus(prevNs)
        return ns.intersects(restNs) && originalProcessor.process(entry.copyWithNs(restNs))
    }
    override fun toString(): String = "ShadowingProcessor($originalProcessor, ns = $ns)"
}

fun <T : ScopeEntry> RsResolveProcessorBase<T>.wrapWithShadowingProcessorAndUpdateScope(
    prevScope: Map<String, Set<Namespace>>,
    currScope: MutableMap<String, Set<Namespace>>,
    ns: Set<Namespace>,
): RsResolveProcessorBase<T> {
    return ShadowingAndUpdateScopeProcessor(this, prevScope, currScope, ns)
}

private class ShadowingAndUpdateScopeProcessor<in T : ScopeEntry>(
    private val originalProcessor: RsResolveProcessorBase<T>,
    private val prevScope: Map<String, Set<Namespace>>,
    private val currScope: MutableMap<String, Set<Namespace>>,
    private val ns: Set<Namespace>,
) : RsResolveProcessorBase<T> {
    override val names: Set<String>? = originalProcessor.names
    override fun process(entry: T): Boolean {
        if (!originalProcessor.acceptsName(entry.name) || entry.name == "_") {
            return originalProcessor.process(entry)
        }
        val prevNs = prevScope[entry.name]
        val newNs = entry.namespaces
        val entryWithIntersectedNs = if (prevNs != null) {
            val restNs = newNs.minus(prevNs)
            if (ns.intersects(restNs)) {
                entry.copyWithNs(restNs)
            } else {
                return false
            }
        } else {
            entry
        }
        currScope[entry.name] = prevNs?.let { it + newNs } ?: newNs
        return originalProcessor.process(entryWithIntersectedNs)
    }
    override fun toString(): String = "ShadowingAndUpdateScopeProcessor($originalProcessor, ns = $ns)"
}

fun <T : ScopeEntry> RsResolveProcessorBase<T>.wrapWithShadowingProcessorAndImmediatelyUpdateScope(
    prevScope: MutableMap<String, Set<Namespace>>,
    ns: Set<Namespace>,
): RsResolveProcessorBase<T> {
    return ShadowingAndImmediatelyUpdateScopeProcessor(this, prevScope, ns)
}

private class ShadowingAndImmediatelyUpdateScopeProcessor<in T : ScopeEntry>(
    private val originalProcessor: RsResolveProcessorBase<T>,
    private val prevScope: MutableMap<String, Set<Namespace>>,
    private val ns: Set<Namespace>,
) : RsResolveProcessorBase<T> {
    override val names: Set<String>? = originalProcessor.names
    override fun process(entry: T): Boolean {
        if (entry.name in prevScope) return false
        val result = originalProcessor.process(entry)
        if (originalProcessor.acceptsName(entry.name)) {
            prevScope[entry.name] = ns
        }
        return result
    }
    override fun toString(): String = "ShadowingAndImmediatelyUpdateScopeProcessor($originalProcessor, ns = $ns)"
}

fun collectResolveVariants(referenceName: String?, f: (RsResolveProcessor) -> Unit): List<MvElement> {
    if (referenceName == null) return emptyList()
    val processor = ResolveVariantsCollector(referenceName)
    f(processor)
    return processor.result
}

private class ResolveVariantsCollector(
    private val referenceName: String,
    val result: MutableList<MvElement> = SmartList(),
): RsResolveProcessorBase<ScopeEntry> {
    override val names: Set<String> = setOf(referenceName)

    override fun process(entry: ScopeEntry): Boolean {
        if (entry.name == referenceName) {
            val element = entry.element
//            if (element !is RsDocAndAttributeOwner || element.existsAfterExpansionSelf) {
            result += element
//            }
        }
        return false
    }
}

fun <T: ScopeEntry> collectResolveVariantsAsScopeEntries(
    referenceName: String?,
    f: (RsResolveProcessorBase<T>) -> Unit
): List<T> {
    if (referenceName == null) return emptyList()
    val processor = ResolveVariantsAsScopeEntriesCollector<T>(referenceName)
    f(processor)
    return processor.result
}

private class ResolveVariantsAsScopeEntriesCollector<T: ScopeEntry>(
    private val referenceName: String,
    val result: MutableList<T> = mutableListOf(),
): RsResolveProcessorBase<T> {
    override val names: Set<String> = setOf(referenceName)

    override fun process(entry: T): Boolean {
        if (entry.name == referenceName) {
//            val element = entry.element
//            if (element !is RsDocAndAttributeOwner || element.existsAfterExpansionSelf) {
            result += entry
//            }
        }
        return false
    }
}

fun collectPathResolveVariants(
    ctx: PathResolutionContext,
    path: MvPath,
    f: (RsResolveProcessor) -> Unit
): List<RsPathResolveResult<MvElement>> {
    val referenceName = path.referenceName ?: return emptyList()
    val processor = SinglePathResolveVariantsCollector(ctx, referenceName)
    f(processor)
    return processor.result
}

private class SinglePathResolveVariantsCollector(
    private val ctx: PathResolutionContext,
    private val referenceName: String,
    val result: MutableList<RsPathResolveResult<MvElement>> = SmartList(),
): RsResolveProcessorBase<ScopeEntry> {
    override val names: Set<String> = setOf(referenceName)

    override fun process(entry: ScopeEntry): Boolean {
        if (entry.name == referenceName) {
            collectPathScopeEntry(ctx, result, entry)
        }
        return false
    }
}

private fun collectPathScopeEntry(
    ctx: PathResolutionContext,
    result: MutableList<RsPathResolveResult<MvElement>>,
    e: ScopeEntry
) {
    val element = e.element
    val visibilityStatus = e.getVisibilityStatusFrom(ctx.context, ctx.lazyContainingModInfo)
//    if (visibilityStatus != VisibilityStatus.CfgDisabled) {
    val isVisible = visibilityStatus == VisibilityStatus.Visible
    // Canonicalize namespaces to consume less memory by the resolve cache
//            val namespaces = when (e.namespaces) {
//                TYPES -> TYPES
//                VALUES -> VALUES
//                TYPES_N_VALUES -> TYPES_N_VALUES
//                else -> e.namespaces
//            }
//    result += element
    result += RsPathResolveResult(element, isVisible)
//    }
}

fun pickFirstResolveVariant(referenceName: String?, f: (RsResolveProcessor) -> Unit): MvElement? =
    pickFirstResolveEntry(referenceName, f)?.element

fun pickFirstResolveEntry(referenceName: String?, f: (RsResolveProcessor) -> Unit): ScopeEntry? {
    if (referenceName == null) return null
    val processor = PickFirstScopeEntryCollector(referenceName)
    f(processor)
    return processor.result
}

private class PickFirstScopeEntryCollector(
    private val referenceName: String,
    var result: ScopeEntry? = null,
): RsResolveProcessorBase<ScopeEntry> {
    override val names: Set<String> = setOf(referenceName)

    override fun process(entry: ScopeEntry): Boolean {
        if (entry.name == referenceName) {
//            val element = entry.element
//            if (element !is RsDocAndAttributeOwner || element.existsAfterExpansionSelf) {
            result = entry
            return true
//            }
        }
        return false
    }
}

//fun collectNames(f: (RsResolveProcessor) -> Unit): Set<String> {
//    val processor = NamesCollector()
//    f(processor)
//    return processor.result
//}

//private class NamesCollector(
//    val result: MutableSet<String> = mutableSetOf(),
//): RsResolveProcessor {
//    override val names: Set<String>? get() = null
//
//    override fun process(entry: SimpleScopeEntry): Boolean {
//        if (entry.name != "_") {
//            result += entry.name
//        }
//        return false
//    }
//}

data class SimpleScopeEntry(
    override val name: String,
    override val element: MvElement,
    override val namespaces: Set<Namespace>,
//    override val subst: Substitution = emptySubstitution
): ScopeEntry {
    override fun doCopyWithNs(namespaces: Set<Namespace>): ScopeEntry = copy(namespaces = namespaces)
}

data class ModInfo(
//    val movePackage: MovePackage?,
    val module: MvModule,
//    val isScript: Boolean,
)

fun <T> Map<String, T>.entriesWithNames(names: Set<String>?): Map<String, T> {
    return if (names.isNullOrEmpty()) {
        this
    } else if (names.size == 1) {
        val single = names.single()
        val value = this[single] ?: return emptyMap()
        mapOf(single to value)
    } else {
        names.mapNotNull { name -> this[name]?.let { name to it } }.toMap()
    }
}

typealias VisibilityFilter = (MvElement, Lazy<ModInfo?>?) -> VisibilityStatus

fun ScopeEntry.getVisibilityStatusFrom(context: MvElement, lazyModInfo: Lazy<ModInfo?>?): VisibilityStatus =
    if (this is ScopeEntryWithVisibility) {
        visibilityFilter(context, lazyModInfo)
    } else {
        VisibilityStatus.Visible
    }


fun ScopeEntry.isVisibleFrom(context: MvElement): Boolean =
    getVisibilityStatusFrom(context, null) == VisibilityStatus.Visible

enum class VisibilityStatus {
    Visible,
    Invisible,
}

data class ScopeEntryWithVisibility(
    override val name: String,
    override val element: MvElement,
    override val namespaces: Set<Namespace>,
    /** Given a [MvElement] (usually [MvPath]) checks if this item is visible in `containingMod` of that element */
    val visibilityFilter: VisibilityFilter,
//    override val subst: Substitution = emptySubstitution,
): ScopeEntry {
    override fun doCopyWithNs(namespaces: Set<Namespace>): ScopeEntry = copy(namespaces = namespaces)
}

fun RsResolveProcessor.process(
    name: String,
    e: MvElement,
    namespaces: Set<Namespace>,
    visibilityFilter: VisibilityFilter
): Boolean = process(ScopeEntryWithVisibility(name, e, namespaces, visibilityFilter))


fun RsResolveProcessor.process(
    name: String,
//    namespaces: Set<Namespace>,
    e: MvElement
): Boolean =
    process(SimpleScopeEntry(name, e, Namespace.none()))
//    process(ScopeEntry(name, e, namespaces))

inline fun RsResolveProcessor.lazy(
    name: String,
//    namespaces: Set<Namespace>,
    e: () -> MvElement?
): Boolean {
    if (!acceptsName(name)) return false
    val element = e() ?: return false
    return process(name, element)
}

fun RsResolveProcessor.process(
    e: MvNamedElement,
//    namespaces: Set<Namespace>
): Boolean {
    val name = e.name ?: return false
    return process(name, e)
}

fun RsResolveProcessor.processAll(
    elements: List<MvNamedElement>,
//    namespaces: Set<Namespace>
): Boolean {
    return elements.any { process(it) }
}

fun RsResolveProcessor.processAll(
    vararg collections: Iterable<MvNamedElement>,
//    namespaces: Set<Namespace>
): Boolean {
    return sequenceOf(*collections).flatten().any { process(it) }
}

fun processAllScopeEntries(elements: List<SimpleScopeEntry>, processor: RsResolveProcessor): Boolean {
    return elements.any { processor.process(it) }
}




