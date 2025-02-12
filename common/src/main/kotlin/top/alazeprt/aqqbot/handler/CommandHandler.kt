package top.alazeprt.aqqbot.handler

import top.alazeprt.aonebot.action.GetGroupMemberInfo
import top.alazeprt.aonebot.action.SendGroupMessage
import top.alazeprt.aonebot.event.message.GroupMessageEvent
import top.alazeprt.aonebot.util.GroupRole
import top.alazeprt.aqqbot.AQQBot
import top.alazeprt.aqqbot.bot.BotProvider

class CommandHandler(val plugin: AQQBot) {
    
    private val config = plugin.getGeneralConfig()
    
    fun handle(message: String, event: GroupMessageEvent): Boolean {
        if (!config.getBoolean("command_execution.enable")) {
            return false
        }
        if (message.split(" ").size < 2) return false
        config.getStringList("command_execution.prefix").forEach prefix@ { prefix ->
            if (message.lowercase().startsWith(prefix.lowercase())) {
                var hasPermission = false
                BotProvider.getBot()?.action(GetGroupMemberInfo(event.groupId, event.senderId)) { member ->
                    config.getStringList("command_execution.allow").forEach {
                        if (it.startsWith("\$")) {
                            if (it == "\$OWNER" && member.role == GroupRole.OWNER) {
                                hasPermission = true
                                return@forEach
                            } else if (it == "\$ADMIN" && (member.role == GroupRole.ADMIN || member.role == GroupRole.OWNER)) {
                                hasPermission = true
                                return@forEach
                            } else if (it == "\$MEMBER") {
                                hasPermission = true
                                return@forEach
                            }
                        } else if (it == event.senderId.toString()) {
                            hasPermission = true
                            return@forEach
                        }
                    }
                    if (!hasPermission) {
                        BotProvider.getBot()?.action(SendGroupMessage(event.groupId,
                            plugin.getMessageManager().get("qq.no_permission")))
                    } else {
                        val commandList = message.split(" ").toMutableList()
                        commandList.removeAt(0)
                        val command = commandList.joinToString(" ")
                        BotProvider.getBot()?.action(SendGroupMessage(event.groupId, plugin.getMessageManager().get("qq.executing_command")))
                        plugin.submitCommand(command).thenAccept {
                            plugin.submitLaterAsync(config.getInt("command_execution.delay")*20L) {
                                if (config.getBoolean("command_execution.format")) {
                                    BotProvider.getBot()?.action(SendGroupMessage(event.groupId,
                                        it.getFormattedString().ifEmpty { plugin.getMessageManager().get("qq.execution_finished") }))
                                } else {
                                    BotProvider.getBot()?.action(SendGroupMessage(event.groupId,
                                        it.getRawString().ifEmpty { plugin.getMessageManager().get("qq.execution_finished") }))
                                }
                            }
                        }
                    }
                }
                return true
            }
        }
        return false
    }
}