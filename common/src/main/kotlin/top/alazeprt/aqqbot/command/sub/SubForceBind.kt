package top.alazeprt.aqqbot.command.sub

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import top.alazeprt.aqqbot.AQQBot
import top.alazeprt.aqqbot.command.ACommand
import top.alazeprt.aqqbot.profile.ASender

class SubForceBind(val plugin: AQQBot) : ACommand {
    override fun onCommand(command: String, sender: ASender, args: List<String>) {
        if (!sender.hasPermission("aqqbot.command.unbind")) {
            sender.sendMessage(Component.text("你没有权限使用该命令!", NamedTextColor.RED))
            return
        }
        if (args.size != 3) {
            sender.sendMessage(Component.text("用法: /aqqbot forcebind <QQ号> <名称>", NamedTextColor.GREEN))
            return
        }
        sender.sendMessage(plugin.getCommandImpl(plugin).forceBind(args[1], args[2]))
    }
}