package org.move.lang.core.resolve2.ref

import org.move.lang.core.psi.MvPath
import org.move.lang.core.psi.ext.allowedNamespaces
import org.move.lang.core.psi.ext.isUseSpeck
import org.move.lang.core.psi.ext.qualifier
import org.move.lang.core.resolve.ref.Namespace
import org.move.lang.core.resolve2.ref.RsPathResolveKind.*
import org.move.lang.core.types.Address
import org.move.lang.moveProject

sealed class RsPathResolveKind {
    /** A path consist of a single identifier, e.g. `foo` */
    data class UnqualifiedPath(val ns: Set<Namespace>): RsPathResolveKind()

    /** `bar` in `foo::bar` or `use foo::{bar}` */
    class QualifiedPath(
        val path: MvPath,
        val qualifier: MvPath,
        val ns: Set<Namespace>,
    ): RsPathResolveKind()

    /** bar in `0x1::bar` */
    class ModulePath(
        val path: MvPath,
        val address: Address,
    ): RsPathResolveKind()

    /** aptos_framework in `use aptos_framework::bar`*/
    data class NamedAddressPath(val address: Address.Named): RsPathResolveKind()
    data class ValueAddressPath(val address: Address.Value): RsPathResolveKind()
}

fun classifyPath(path: MvPath, overwriteNs: Set<Namespace>? = null): RsPathResolveKind {
    val qualifier = path.qualifier

    val ns = overwriteNs ?: path.allowedNamespaces()
//        if (qualifier == null) {
//            return UnqualifiedPath(ns)
//        }
    val isUseSpeck = path.isUseSpeck
    if (qualifier == null) {
        // left-most path
        if (isUseSpeck) {
            // use aptos_framework::
            //     //^
            val moveProject = path.moveProject
            val pathName = path.referenceName
            if (pathName == null) {
                val value = path.pathAddress!!.text
                return ValueAddressPath(Address.Value(value))
            } else {
                val namedAddress =
                    moveProject?.getNamedAddress(pathName) ?: Address.Named(pathName, null, moveProject)
                return NamedAddressPath(namedAddress)
            }
        }
        return UnqualifiedPath(ns)
    }

    val qualifierPath = qualifier.path
    val pathAddress = qualifier.pathAddress
    val qualifierName = qualifier.referenceName

    return when {
        qualifierPath == null && pathAddress != null -> ModulePath(path, Address.Value(pathAddress.text))
        qualifierPath == null && isUseSpeck && qualifierName != null -> {
            val moveProject = qualifier.moveProject
            val namedAddress =
                moveProject?.getNamedAddress(qualifierName)
                    ?: Address.Named(qualifierName, null, moveProject)
            ModulePath(path, namedAddress)
        }
        else -> QualifiedPath(path, qualifier, ns)
    }
}
