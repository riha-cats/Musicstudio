package org.rhsd.musicStudio.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

// =================================================================
// GUI 리스너
// =================================================================
// 에디터/악기/설정 인벤토리 이벤트를 받아 GuiManager 로 위임
// 우리 GUI 안에서는 아이템 이동을 모두 취소한다 (클릭은 버튼 동작으로만 사용)
public final class GuiListener implements Listener {

    private final GuiManager gui;

    public GuiListener(GuiManager gui) {
        this.gui = gui;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        Object holder = top.getHolder();

        // [A] :: 에디터 인벤토리인가?
        if (holder instanceof EditorHolder) {
            event.setCancelled(true);
            // 하단(플레이어) 인벤 클릭은 무시
            if (event.getClickedInventory() != top) {
                return;
            }
            if (event.getWhoClicked() instanceof Player player) {
                gui.handleEditorClick(player, event.getRawSlot(), event.getClick());
            }
        }
        // [B] :: 악기 선택 메뉴인가?
        else if (holder instanceof InstrumentMenu menu) {
            event.setCancelled(true);
            if (event.getClickedInventory() != top) {
                return;
            }
            if (event.getWhoClicked() instanceof Player player) {
                gui.handleInstrumentClick(player, menu, event.getRawSlot());
            }
        }
        // [C] :: 설정 메뉴인가?
        else if (holder instanceof SettingsMenu menu) {
            event.setCancelled(true);
            if (event.getClickedInventory() != top) {
                return;
            }
            if (event.getWhoClicked() instanceof Player player) {
                gui.handleSettingsClick(player, menu, event.getRawSlot());
            }
        }
        // [D] :: 음반 추출 메뉴인가?
        else if (holder instanceof OutputMenu menu) {
            event.setCancelled(true);
            if (event.getClickedInventory() != top) {
                return;
            }
            if (event.getWhoClicked() instanceof Player player) {
                gui.handleOutputClick(player, menu, event.getRawSlot());
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MsHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MsHolder
                && event.getPlayer() instanceof Player player) {
            gui.onInventoryClose(player);
        }
    }
}

// 컴플리트
