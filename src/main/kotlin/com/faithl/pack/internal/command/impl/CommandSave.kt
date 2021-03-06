package com.faithl.pack.internal.command.impl

import com.faithl.pack.internal.database.Database
import com.faithl.pack.internal.util.sendLangIfEnabled
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.subCommand

object CommandSave {

    val command = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            Database.cache.forEach { (key, value) ->
                value.data.forEach {
                    Database.INSTANCE.setPackData(key, it)
                }
            }
            sender.sendLangIfEnabled("command-save-info")
        }
    }

}