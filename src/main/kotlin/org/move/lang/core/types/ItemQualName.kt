package org.move.lang.core.types

import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import org.move.lang.core.psi.MvQualNamedElement
import org.move.lang.moveProject
import org.move.openapiext.readUTFFastAsNullable
import org.move.openapiext.writeUTFFastAsNullable

data class ItemQualName(
    val address: Address,
    val moduleName: String?,
    val itemName: String
) {
    fun editorText(): String {
        val addressText = when (address) {
            is Address.Named -> address.name
            is Address.Value -> address.value
        }
        return listOfNotNull(addressText, moduleName, itemName).joinToString("::")
    }

    fun cmdText(): String {
        val addressText = address.canonicalValue(null)
        return listOfNotNull(addressText, moduleName, itemName).joinToString("::")
    }

    fun shortCmdText(): String {
        val addressText = address.shortenedValue(null)
        return listOfNotNull(addressText, moduleName, itemName).joinToString("::")
    }

//    fun cmdTextWithShortenedAddress(): String {
//        val addressText = address.value
//        return listOfNotNull(addressText, moduleName, itemName).joinToString("::")
//    }

    companion object {
        val DEFAULT_MOD_FQ_NAME: ItemQualName =
            ItemQualName(Address.Value("0x0"), null, "default")
        val ANY_SCRIPT: ItemQualName =
            ItemQualName(Address.Value("0x0"), null, "script")

        fun fromCmdText(text: String): ItemQualName? {
            val parts = text.split("::")
            val address = parts.getOrNull(0) ?: return null
            val moduleName = parts.getOrNull(1) ?: return null
            val itemName = parts.getOrNull(2) ?: return null
            return ItemQualName(Address.Value(address), moduleName, itemName)
        }

        fun serialize(qualName: ItemQualName?, dataStream: StubOutputStream) {
            with(dataStream) {
                writeUTFFastAsNullable(qualName?.address?.text())
                writeUTFFastAsNullable(qualName?.moduleName)
                writeUTFFastAsNullable(qualName?.itemName)
            }
        }

        fun deserialize(dataStream: StubInputStream): ItemQualName? {
            val addressText = dataStream.readUTFFastAsNullable() ?: return null
            val address =
                if ("=" in addressText) {
                    val parts = addressText.split("=")
                    val name = parts[0].trim()
                    val value = parts[1].trim()
                    Address.Named(name, value)
                } else {
                    Address.Value(addressText)
                }
            val moduleName = dataStream.readUTFFastAsNullable()
            val itemName = dataStream.readUTFFastAsNullable() ?: return null
            return ItemQualName(address, moduleName, itemName)
        }
    }
}
