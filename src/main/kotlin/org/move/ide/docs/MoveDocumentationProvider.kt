package org.move.ide.docs

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.move.lang.core.psi.MoveNamedAddress
import org.move.lang.core.psi.ext.addressValue
import org.move.lang.core.types.HasType

class MoveDocumentationProvider : AbstractDocumentationProvider() {
    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        if (element is MoveNamedAddress) {
//            // TODO: add docs for both scopes
            return element.addressValue
        }
        if (element !is HasType) return null
        val type = element.resolvedType(emptyMap()) ?: return null
        return type.typeLabel(element)
    }

    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int
    ): PsiElement? {
        if (contextElement is MoveNamedAddress) return contextElement
        return super.getCustomDocumentationElement(editor, file, contextElement, targetOffset)
    }
}
