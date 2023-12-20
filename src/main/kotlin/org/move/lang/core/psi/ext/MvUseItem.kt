package org.move.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.move.lang.core.psi.*
import org.move.lang.core.psi.impl.MvNamedElementImpl
import org.move.lang.core.resolve.ItemVis
import org.move.lang.core.resolve.MslLetScope
import org.move.lang.core.resolve.ref.MvPolyVariantReferenceCached
import org.move.lang.core.resolve.ref.Namespace
import org.move.lang.core.resolve.ref.Visibility
import org.move.lang.core.resolve.resolveModuleItem

fun MvUseItem.useSpeck(): MvItemUseSpeck =
    ancestorStrict() ?: error("ItemImport outside ModuleItemsImport")

val MvUseItem.annotationItem: MvElement
    get() {
        val parent = this.parent
        if (parent is MvUseItemGroup && parent.useItemList.size != 1) return this
        return useStmt
    }

val MvUseItem.useStmt: MvUseStmt
    get() =
        ancestorStrict() ?: error("always has MvUseStmt as ancestor")

val MvUseItem.nameOrAlias: String?
    get() {
        val alias = this.useAlias
        if (alias != null) {
            return alias.identifier?.text
        }
        return this.identifier.text
    }

val MvUseItem.moduleName: String
    get() {
        val useStmt = this.ancestorStrict<MvUseStmt>()
        return useStmt?.itemUseSpeck?.fqModuleRef?.referenceName.orEmpty()
    }

val MvUseItem.isSelf: Boolean get() = this.identifier.textMatches("Self")

class MvUseItemReferenceElement(
    element: MvUseItem
) : MvPolyVariantReferenceCached<MvUseItem>(element) {

    override fun multiResolveInner(): List<MvNamedElement> {
        val fqModuleRef = element.useSpeck().fqModuleRef
        val module =
            fqModuleRef.reference?.resolve() as? MvModule ?: return emptyList()
        if ((element.useAlias == null && element.text == "Self")
            || (element.useAlias != null && element.text.startsWith("Self as"))
        ) {
            return listOf(module)
        }

        val ns = setOf(
            Namespace.TYPE,
            Namespace.NAME,
            Namespace.FUNCTION,
            Namespace.SCHEMA,
            Namespace.ERROR_CONST
        )
        val vs = Visibility.buildSetOfVisibilities(fqModuleRef)

        // import has MAIN+VERIFY, and TEST if it or any of the parents has test
        val useItemScopes = mutableSetOf(ItemScope.MAIN, ItemScope.VERIFY)

        var scopedElement: MvElement? = element
        while (scopedElement != null) {
            useItemScopes.addAll(scopedElement.itemScopes)
            scopedElement = scopedElement.parent as? MvElement
        }

        val itemVis = ItemVis(
            vs,
            MslLetScope.EXPR_STMT,
            itemScopes = useItemScopes,
        )
        return resolveModuleItem(
            module,
            element.referenceName,
            ns,
            itemVis
        )
    }

}

abstract class MvUseItemMixin(node: ASTNode) : MvNamedElementImpl(node),
                                               MvUseItem {
    override fun getName(): String? {
        val name = super.getName()
        if (name != "Self") return name
        return ancestorStrict<MvItemUseSpeck>()?.fqModuleRef?.referenceName ?: name
    }

    override val referenceNameElement: PsiElement get() = identifier

    override fun getReference() = MvUseItemReferenceElement(this)
}
