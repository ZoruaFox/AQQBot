package top.alazeprt.aqqbot.event

import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.event.player.PlayerChatEvent
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.platform.Ghost
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import top.alazeprt.aonebot.action.SendGroupMessage
import top.alazeprt.aqqbot.AQQBot
import top.alazeprt.aqqbot.AQQBot.config
import top.alazeprt.aqqbot.AQQBot.dataMap
import top.alazeprt.aqqbot.AQQBot.isFileStorage
import top.alazeprt.aqqbot.AQQBot.verifyCodeMap
import top.alazeprt.aqqbot.api.events.ServerToGroupEvent
import top.alazeprt.aqqbot.util.AFormatter
import top.alazeprt.aqqbot.util.AI18n.get
import top.alazeprt.aqqbot.util.DBQuery
import top.alazeprt.aqqbot.util.DBQuery.playerInDatabase
import java.util.*
import java.util.function.Consumer

object AGameEvent {

    val formatter = AFormatter()

    // Bukkit
    @Ghost
    @SubscribeEvent
    fun onJoin(event: PlayerJoinEvent) {
        if (AQQBot.updateConfig && event.player.isOp) {
            event.player.sendMessage("§a检测到你正在使用 AQQBot 的低版本配置文件, 这可能会引起一些问题")
            event.player.sendMessage("§a插件已自动释放新版本配置文件并命名为 config_new.yml, 请根据你的旧版本配置文件 (config.yml) 修改该文件并重命名为 config.yml, 最后执行 /aqqbot reload 应用修改")
        }
        val handle = whitelistHandler(event.player.name) {
            event.player.kickPlayer(it)
        }
        if (!handle) playerStatusHandler(event.player.name, true)
    }

    @Ghost
    @SubscribeEvent
    fun onQuit(event: PlayerQuitEvent) {
        playerStatusHandler(event.player.name, false)
    }

    @Ghost
    @SubscribeEvent(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onChat(event: AsyncPlayerChatEvent) {
        if (canForwardMessage(event.message) != null) {
            submit (async = true) {
                val message = canForwardMessage(event.message)!!
                val internalEvent = ServerToGroupEvent(event.player.name, message)
                AQQBot.postEvent(internalEvent)
                internalEvent.handle()
            }
        }
    }

    // Velocity
    @Ghost
    @SubscribeEvent
    fun onVCJoin(event: LoginEvent) {
        val handle = whitelistHandler(event.player.username) {
            event.player.disconnect(Component.text(it))
        }
        if (!handle) playerStatusHandler(event.player.username, true)
    }

    @Ghost
    @SubscribeEvent
    fun onVCQuit(event: DisconnectEvent) {
        playerStatusHandler(event.player.username, false)
    }

    @Ghost
    @SubscribeEvent(ignoreCancelled = true)
    fun onVCChat(event: PlayerChatEvent) {
        if (canForwardMessage(event.message) != null) {
            submit (async = true) {
                val message = canForwardMessage(event.message)!!
                val internalEvent = ServerToGroupEvent(event.player.username, message)
                AQQBot.postEvent(internalEvent)
                internalEvent.handle()
            }
        }
    }

    private fun playerStatusHandler(playerName: String, isJoin: Boolean) {
        if (config.getBoolean("notify.player_status.enable")) {
            var qq: Long = -1
            if (isFileStorage) {
                dataMap.forEach { k, v ->
                    if (v.contains(playerName)) qq = k.toLong()
                }
            } else {
                qq = if (DBQuery.getUserIdByName(playerName) == null) -1 else DBQuery.getUserIdByName(playerName)!!.toLong()
            }
            submit (async = true) {
                AQQBot.enableGroups.forEach {
                    AQQBot.oneBotClient.action(SendGroupMessage(it.toLong(), config.getString("notify.player_status.${if (isJoin) "join" else "leave"}")!!
                        .replace("\${playerName}", playerName)
                        .replace("\${userId}", qq.toString()), true))
                }
            }
        }
    }
    
    private fun whitelistHandler(playerName: String, kickMethod: Consumer<String>): Boolean {
        if (!config.getBoolean("whitelist.enable") || !config.getBoolean("whitelist.need_bind_to_login")) return false

        if (isFileStorage) {
            dataMap.values.forEach {
                if (it.contains(playerName)) return false
            }
            if (config.getString("whitelist.verify_method")?.uppercase() == "GROUP_NAME") {
                kickMethod.accept(AFormatter.pluginToChat(get("game.not_bind", mutableMapOf(Pair("command", config.getStringList("whitelist.prefix.bind")[0])))))
                return true
            } else if (config.getString("whitelist.verify_method")?.uppercase() == "VERIFY_CODE") {
                val verifyCode = if (verifyCodeMap.containsKey(playerName)) verifyCodeMap.get(playerName)!!.first else UUID.randomUUID().toString().substring(0, 6)
                kickMethod.accept(AFormatter.pluginToChat(get("game.not_verified", mutableMapOf(Pair("command", config.getStringList("whitelist.prefix.bind")[0]), Pair("code", verifyCode)))))
                if (!verifyCodeMap.containsKey(playerName)) verifyCodeMap.put(playerName, Pair(verifyCode, System.currentTimeMillis()))
                return true
            }
        }
        if (!isFileStorage && !playerInDatabase(playerName)) {
            if (config.getString("whitelist.verify_method")?.uppercase() == "GROUP_NAME") {
                kickMethod.accept(AFormatter.pluginToChat(get("game.not_bind", mutableMapOf(Pair("command", config.getStringList("whitelist.prefix.bind")[0])))))
                return true
            } else if (config.getString("whitelist.verify_method")?.uppercase() == "VERIFY_CODE") {
                val verifyCode = if (verifyCodeMap.containsKey(playerName)) verifyCodeMap.get(playerName)!!.first else UUID.randomUUID().toString().substring(0, 6)
                kickMethod.accept(AFormatter.pluginToChat(get("game.not_verified", mutableMapOf(Pair("command", config.getStringList("whitelist.prefix.bind")[0]), Pair("code", verifyCode)))))
                if (!verifyCodeMap.containsKey(playerName)) verifyCodeMap.put(playerName, Pair(verifyCode, System.currentTimeMillis()))
                return true
            }
        }
        return false
    }

    private fun canForwardMessage(message: String): String? {
        if (!config.getBoolean("chat.server_to_group.enable")) {
            return null
        }
        var formattedMessage = formatter.regexFilter(config.getStringList("chat.server_to_group.filter"), message)
        if (config.getBoolean("chat.server_to_group.default_format")) {
            formattedMessage = AFormatter.chatClear(formattedMessage)
        }
        if (formattedMessage.length > config.getInt("chat.max_forward_length")) {
            formattedMessage = formattedMessage.substring(0, config.getInt("chat.max_forward_length")) + "..."
        }
        if (config.getStringList("chat.server_to_group.prefix").contains("")) {
            return formattedMessage
        }
        config.getStringList("chat.server_to_group.prefix").forEach {
             if (formattedMessage.startsWith(it)) {
                 return formattedMessage.substring(it.length)
             }
        }
        return null
    }
}
