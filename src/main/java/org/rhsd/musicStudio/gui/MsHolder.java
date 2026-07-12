package org.rhsd.musicStudio.gui;

import org.bukkit.inventory.InventoryHolder;

// 이 플러그인이 띄운 인벤토리의 공통 마커
// holder instanceof MsHolder 한 번으로 에디터/악기/설정 GUI 를 모두 가려낸다
public interface MsHolder extends InventoryHolder {
}
