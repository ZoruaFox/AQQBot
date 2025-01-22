package top.alazeprt.aqqbot.handler

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import taboolib.common.platform.function.submit
import taboolib.platform.VelocityPlugin
import top.alazeprt.aonebot.action.GetGroupMemberList
import top.alazeprt.aonebot.action.SendGroupMessage
import top.alazeprt.aonebot.event.message.GroupMessageEvent
import top.alazeprt.aqqbot.AQQBot
import top.alazeprt.aqqbot.AQQBot.config
import top.alazeprt.aqqbot.AQQBot.isBukkit
import top.alazeprt.aqqbot.AQQBot.isFileStorage
import top.alazeprt.aqqbot.debug.ALogger
import top.alazeprt.aqqbot.util.AI18n.get
import top.alazeprt.aqqbot.util.DBQuery
import top.alazeprt.aqqbot.util.DBQuery.addPlayer
import top.alazeprt.aqqbot.util.DBQuery.playerInDatabase
import top.alazeprt.aqqbot.util.DBQuery.qqInDatabase
import top.alazeprt.aqqbot.util.DBQuery.removePlayer

class WhitelistAdminHandler {
    companion object {
        private fun bind(operatorId: String, userId: String, groupId: Long, playerName: String) {
            if (isFileStorage && AQQBot.dataMap.containsKey(userId) && AQQBot.dataMap[userId]!!.size >= config.getLong("whitelist.max_bind_count")) {
                AQQBot.dataMap.remove(userId)
            } else if (!isFileStorage && qqInDatabase(userId.toLong()) != null && qqInDatabase(userId.toLong()).size >= config.getLong("whitelist.max_bind_count")) {
                DBQuery.removePlayerByUserId(userId.toLong())
            }
            if (!validateName(playerName)) {
                AQQBot.oneBotClient.action(SendGroupMessage(groupId, get("qq.whitelist.invalid_name"), true))
                return
            }
            if (isFileStorage) {
                var existUserId = ""
                AQQBot.dataMap.forEach { k, v ->
                    if (v.contains(playerName)) {
                        existUserId = k
                        return@forEach
                    }
                }
                if (!existUserId.isEmpty()) {
                    if (AQQBot.dataMap.get(existUserId)!!.size == 1) AQQBot.dataMap.remove(existUserId)
                    else AQQBot.dataMap[existUserId] = AQQBot.dataMap[existUserId]!!.filter { it != playerName }.toMutableList()
                }
            } else {
                if (playerInDatabase(playerName)) {
                    DBQuery.removePlayerByName(playerName)
                }
            }
            val newList: MutableList<String> = AQQBot.dataMap.getOrDefault(userId, mutableListOf())
            newList.add(playerName)
            if (isFileStorage) AQQBot.dataMap[userId] = newList
            else addPlayer(userId.toLong(), playerName)
            AQQBot.oneBotClient.action(SendGroupMessage(groupId, get("qq.whitelist.bind_successful"), true))
            ALogger.log("$operatorId bind $userId to account $playerName")
        }

        private fun unbind(operatorId: String, userId: String, groupId: Long, playerName: String) {
            if (isFileStorage && !AQQBot.dataMap.containsKey(userId)) {
                AQQBot.oneBotClient.action(SendGroupMessage(groupId, get("qq.whitelist.admin.not_bind", mutableMapOf(Pair("userId", userId))), true))
                return
            } else if (!isFileStorage && qqInDatabase(userId.toLong()) == null) {
                AQQBot.oneBotClient.action(SendGroupMessage(groupId, get("qq.whitelist.admin.not_bind", mutableMapOf(Pair("userId", userId))), true))
                return
            }
            if (isFileStorage) {
                AQQBot.dataMap.forEach { (k, v) ->
                    if (v.contains(playerName) && k == userId) {
                        if (v.size == 1) AQQBot.dataMap.remove(k)
                        else AQQBot.dataMap[k] = v.filter { it != playerName }.toMutableList()
                        AQQBot.oneBotClient.action(SendGroupMessage(groupId, get("qq.whitelist.unbind_successful"), true))
                        ALogger.log("$operatorId unbind $userId to account $playerName")
                        submit {
                            if (isBukkit) {
                                for (player in Bukkit.getOnlinePlayers()) {
                                    if (player.name == playerName) {
                                        player.kickPlayer(get("game.kick_when_unbind"))
                                    }
                                }
                            } else {
                                for (player in VelocityPlugin.getInstance().server.allPlayers) {
                                    if (player.username == playerName) {
                                        player.disconnect(Component.text(get("game.kick_when_unbind")))
                                    }
                                }
                            }
                        }
                        return
                    } else if (k == userId) {
                        AQQBot.oneBotClient.action(SendGroupMessage(groupId, get("qq.whitelist.admin.bind_by_other", mutableMapOf(Pair("name", v.joinToString(", ")))), true))
                        return
                    }
                }
            } else {
                if (!qqInDatabase(userId.toLong()).contains(playerName)) {
                    AQQBot.oneBotClient.action(SendGroupMessage(groupId, get("qq.whitelist.admin.bind_by_other", mutableMapOf(Pair("name", qqInDatabase(userId.toLong()).joinToString(", ")))), true))
                }
                removePlayer(userId.toLong(), playerName)
                AQQBot.oneBotClient.action(SendGroupMessage(groupId, get("qq.whitelist.unbind_successful"), true))
                ALogger.log("$operatorId unbind $userId to account $playerName")
                submit {
                    if (isBukkit) {
                        for (player in Bukkit.getOnlinePlayers()) {
                            if (player.name == playerName) {
                                player.kickPlayer(get("game.kick_when_unbind"))
                            }
                        }
                    } else {
                        for (player in VelocityPlugin.getInstance().server.allPlayers) {
                            if (player.username == playerName) {
                                player.disconnect(Component.text(get("game.kick_when_unbind")))
                            }
                        }
                    }
                }
            }
            AQQBot.oneBotClient.action(SendGroupMessage(groupId, get("qq.whitelist.admin.invalid_bind", mutableMapOf(Pair("userId", userId))), true))
        }

        fun validateName(name: String): Boolean {
            val regex = "^\\w+\$"
            return name.matches(regex.toRegex())
        }

        fun handle(message: String, event: GroupMessageEvent, action: String) {
            if (!AQQBot.config.getBoolean("whitelist.admin")) {
                return
            }
            val userId = message.split(" ")[1].toLongOrNull()
            if (userId == null) {
                AQQBot.oneBotClient.action(SendGroupMessage(event.groupId, get("qq.whitelist.admin.invalid_user_id"), true))
                return
            }
            val playerName = message.split(" ")[2]
            AQQBot.oneBotClient.action(GetGroupMemberList(event.groupId)) {
                var has = false;
                for (member in it) {
                    if (member.member.userId == userId) {
                        has = true
                        break
                    }
                }
                if (!has) {
                    AQQBot.oneBotClient.action(SendGroupMessage(event.groupId, get("qq.whitelist.admin.user_not_in_group"), true))
                    return@action
                } else {
                    if (action == "bind") {
                        bind(event.senderId.toString(), userId.toString(), event.groupId, playerName)
                    } else if (action == "unbind") {
                        unbind(event.senderId.toString(), userId.toString(), event.groupId, playerName)
                    }
                }
            }
        }
    }
}