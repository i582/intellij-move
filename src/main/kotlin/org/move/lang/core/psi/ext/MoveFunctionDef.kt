package org.move.lang.core.psi.ext

import org.move.lang.core.psi.MvFunctionDef

val MvFunctionDef.isTest: Boolean get() =
    this.attrList.findSingleItemAttr("test") != null

//val MvFunctionDef.params: List<MvFunctionParameter>
//    get() =
//        emptyList()
//        this.functionParameterList?.functionParameterList.orEmpty()

//val MvFunctionDef.isPublic: Boolean
//    get() = isChildExists(MvElementTypes.PUBLIC)
