package com.faithl.pack.internal.listener

import com.faithl.pack.api.FaithlPackAPI
import com.faithl.pack.api.event.*
import com.faithl.pack.internal.database.Database
import com.faithl.pack.internal.util.condition
import com.faithl.pack.internal.util.sendLangIfEnabled
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.platform.Schedule
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.adaptPlayer
import taboolib.common.platform.function.submit
import taboolib.common.util.asList
import taboolib.module.kether.KetherShell
import taboolib.module.nms.getItemTag
import taboolib.platform.util.isAir

/**
 * @author Leosouthey
 * @since 2022/4/30-19:48
 **/
object Pack {

    val cache = ArrayList<Player>()

    @SubscribeEvent
    fun e(e: PackPlaceItemEvent) {
        val itemStack = e.currentItem ?: return
        if (itemStack.isAir()) {
            return
        }
        itemStack.getItemTag().getDeep("pack.type")?.let {
            return
        }
        if (condition(e.player, e.packData.getSetting(), "ban-condition", itemStack)) {
            e.player.sendLangIfEnabled("pack-place-error", e.packData.name)
            e.isCancelled = true
            return
        }
        if (e.packData.getSetting().sort?.getBoolean("must-condition") == true) {
            if (e.action == InventoryAction.PICKUP_ALL
                || e.action == InventoryAction.PICKUP_HALF
                || e.action == InventoryAction.PLACE_ONE
                || e.action == InventoryAction.PICKUP_SOME
                || e.action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || e.action == InventoryAction.SWAP_WITH_CURSOR
                || e.action == InventoryAction.HOTBAR_SWAP
            ) {
                if (!condition(e.player, e.packData.getSetting(), "condition", itemStack)) {
                    e.player.sendLangIfEnabled("pack-place-error", e.packData.name)
                    e.isCancelled = true
                    return
                }
            }
        }
    }

    @SubscribeEvent
    fun e(e: PackClickEvent) {
        val item = e.item ?: return
        if (item.isAir()) {
            return
        }
        val type = item.getItemTag().getDeep("pack.type") ?: return
        val pack = e.packData.getSetting()
        e.isCancelled = true
        when (type.asString()) {
            "page" -> {
                if (e.isLeftClick) {
                    val event = PackPageSwitchEvent(
                        e.player, e.clicker, e.inventory, e.packData, e.page, e.page + 1,
                    )
                    if (event.call()) {
                        e.packData.open(event.player, event.targetPage)
                    }
                } else if (e.isRightClick) {
                    val event = PackPageSwitchEvent(
                        e.player, e.clicker, e.inventory, e.packData, e.page, e.page - 1,
                    )
                    if (event.call()) {
                        e.packData.open(event.opener, event.targetPage)
                    }
                }
            }

            "locked" -> {
                if (e.isLeftClick) {
                    KetherShell.eval(
                        source = pack.inventory!!["items.locked.action.left-click"]?.toString()
                            ?.asList() ?: return,
                        sender = adaptPlayer(e.clicker),
                        namespace = listOf("faithlpack", "faithlpack-internal")
                    )
                } else if (e.isRightClick) {
                    KetherShell.eval(
                        source = pack.inventory!!["items.locked.action.right-click"]?.toString()
                            ?.asList() ?: return,
                        sender = adaptPlayer(e.clicker),
                        namespace = listOf("faithlpack", "faithlpack-internal")
                    )
                }
            }

            "setting.auto-pickup" -> {
                if (pack.sort?.getBoolean("auto-pickup.enabled") == null || !pack.sort.getBoolean("auto-pickup.enabled")) {
                    e.clicker.sendLangIfEnabled("pack-auto-pickup-error")
                    return
                }
                if (pack.permission != null && !e.clicker.hasPermission(pack.permission)) {
                    e.clicker.sendLangIfEnabled("pack-auto-pickup-no-perm")
                    return
                }
                val autoPickupPermission = pack.sort.getString("auto-pickup.permission")
                if (autoPickupPermission != null && !e.player.hasPermission(autoPickupPermission)) {
                    e.clicker.sendLangIfEnabled("pack-auto-pickup-no-perm")
                    return
                }
                if (Database.INSTANCE.getPackOption(e.clicker.uniqueId, pack.name, "auto-pickup").toBoolean()) {
                    Database.INSTANCE.setPackOption(e.player.uniqueId, pack.name, "auto-pickup", false.toString())
                    e.clicker.sendLangIfEnabled("pack-auto-pickup-off", pack.name)
                    return
                } else {
                    Database.INSTANCE.setPackOption(e.player.uniqueId, pack.name, "auto-pickup", true.toString())
                    e.clicker.sendLangIfEnabled("pack-auto-pickup-on", pack.name)
                    return
                }
            }
        }
    }

    @SubscribeEvent
    fun e(e: PlayerQuitEvent) {
        Database.cache.remove(e.player.uniqueId)
    }

    @SubscribeEvent
    fun e(e: PackCloseEvent) {
        PackSaveEvent(e.player, e.opener, e.packData, e.page, e.inventory).call()
    }

    @SubscribeEvent
    fun e(e: PackSaveEvent) {
        if (!cache.contains(e.player)) {
            cache.add(e.player)
            submit(async = true) {
                e.packData.setPageItems(e.page, e.inventory)
                FaithlPackAPI.save(e.player, e.packData)
                cache.remove(e.player)
            }
        }
    }

    @SubscribeEvent
    fun e(e: PackPageSwitchEvent) {
        PackSaveEvent(e.player, e.opener, e.packData, e.previousPage, e.previousInventory).call()
    }

    @Schedule(period = 20 * 60)
    fun autoSave() {
        FaithlPackAPI.openingPacks.toList().forEach {
            if (!cache.contains(it.player)) {
                cache.add(it.player)
                submit(async = true) {
                    it.packData.setPageItems(it.page, it.inventory)
                    FaithlPackAPI.save(it.player, it.packData)
                    cache.remove(it.player)
                }
            }
        }
    }

}