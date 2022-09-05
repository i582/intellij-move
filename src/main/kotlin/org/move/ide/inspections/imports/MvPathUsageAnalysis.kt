package org.move.ide.inspections.imports

import com.intellij.psi.util.CachedValuesManager.getProjectPsiDependentCache
import com.intellij.psi.util.PsiTreeUtil
import org.move.lang.core.psi.*
import org.move.lang.core.psi.ext.allModuleSpecBlocks
import org.move.lang.core.psi.ext.itemScope
import org.move.lang.core.psi.ext.module
import org.move.lang.core.psi.ext.moduleSpec
import org.move.lang.core.resolve.ItemScope

typealias ItemUsages = MutableMap<String, MutableSet<MvNamedElement>>

data class ScopePathUsages(
    val nameUsages: ItemUsages,
    val typeUsages: ItemUsages,
) {
    fun updateFrom(other: ScopePathUsages) {
        nameUsages.putAll(other.nameUsages)
        typeUsages.putAll(other.typeUsages)
    }

    fun all(): ItemUsages {
        val usages = nameUsages.toMutableMap()
        usages.putAll(typeUsages)
        return usages
    }
}

data class PathUsages(
    val mainUsages: ScopePathUsages,
    val testUsages: ScopePathUsages
) {
    fun updateFrom(other: PathUsages) {
        mainUsages.updateFrom(other.mainUsages)
        testUsages.updateFrom(other.testUsages)
    }

    fun get(itemScope: ItemScope): ScopePathUsages {
        return when (itemScope) {
            ItemScope.MAIN -> mainUsages
            ItemScope.TEST -> testUsages
        }
    }
}

val MvImportsOwner.pathUsages: PathUsages
    get() {
        val localPathUsages = this.localPathUsages()
        when (this) {
            is MvModuleBlock -> {
                for (specBlock in this.module.allModuleSpecBlocks()) {
                    localPathUsages.updateFrom(specBlock.localPathUsages())
                }
            }
            is MvModuleSpecBlock -> {
                val module = this.moduleSpec.module ?: return localPathUsages
                val moduleBlock = module.moduleBlock
                if (moduleBlock != null) {
                    localPathUsages.updateFrom(moduleBlock.localPathUsages())
                }
                for (specBlock in module.allModuleSpecBlocks().filter { it != this }) {
                    localPathUsages.updateFrom(specBlock.localPathUsages())
                }
            }
        }
        return localPathUsages
    }

private fun MvImportsOwner.localPathUsages(): PathUsages {
    return getProjectPsiDependentCache(this) {

        val mainNameUsages = mutableMapOf<String, MutableSet<MvNamedElement>>()
        val mainTypeUsages = mutableMapOf<String, MutableSet<MvNamedElement>>()
        val testNameUsages = mutableMapOf<String, MutableSet<MvNamedElement>>()
        val testTypeUsages = mutableMapOf<String, MutableSet<MvNamedElement>>()

        for (child in it.children) {
            PsiTreeUtil.processElements(child) { element ->
                when {
                    element is MvPathType -> {
                        val typeUsages =
                            if (element.itemScope == ItemScope.TEST) testTypeUsages else mainTypeUsages
                        putUsage(element.path, typeUsages)
                        true
                    }
                    element is MvPath && element.parent !is MvPathType -> {
                        val nameUsages =
                            if (element.itemScope == ItemScope.TEST) testNameUsages else mainNameUsages
                        putUsage(element, nameUsages)
                        true
                    }
                    else -> true
                }
            }
        }
        PathUsages(
            ScopePathUsages(mainNameUsages, mainTypeUsages),
            ScopePathUsages(testNameUsages, testTypeUsages),
        )
    }
}

private fun putUsage(element: MvPath, itemUsages: ItemUsages) {
    val moduleRef = element.moduleRef
    when {
        // MODULE::ITEM
        moduleRef != null && moduleRef !is MvFQModuleRef -> {
            val modName = moduleRef.referenceName ?: return
            val targets = moduleRef.reference?.multiResolve().orEmpty()
            if (targets.isEmpty()) {
                itemUsages.putIfAbsent(modName, mutableSetOf())
            } else {
                val items = itemUsages.getOrPut(modName) { mutableSetOf() }
                targets.forEach {
                    items.add(it)
                }
            }
        }
        // ITEM_NAME
        moduleRef == null -> {
            val name = element.referenceName ?: return
            val targets = element.reference?.multiResolve().orEmpty()
            if (targets.isEmpty()) {
                itemUsages.putIfAbsent(name, mutableSetOf())
            } else {
                val items = itemUsages.getOrPut(name) { mutableSetOf() }
                targets.forEach {
                    items.add(it)
                }
            }
        }
    }
}
