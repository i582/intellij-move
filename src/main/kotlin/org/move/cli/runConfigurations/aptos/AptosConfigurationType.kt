package org.move.cli.runConfigurations.aptos

import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.util.NotNullLazyValue
import org.move.ide.MoveIcons

class AptosConfigurationType :
    ConfigurationTypeBase(
        "AptosCommandConfiguration",
        "Aptos",
        "Aptos command execution",
        NotNullLazyValue.createConstantValue(MoveIcons.APTOS)
    ) {
    companion object {
        fun getInstance(): AptosConfigurationType {
            return ConfigurationTypeUtil.findConfigurationType(AptosConfigurationType::class.java)
        }
    }
}
