package org.rhsd.musicStudio.gui;

import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

// 에디터 인벤토리 식별용 holder
// songId 를 함께 들고 있어 재렌더 시 대상 곡이 맞는지 확인한다
public final class EditorHolder implements MsHolder {

    private final String songId;
    private Inventory inventory;

    public EditorHolder(String songId) {
        this.songId = songId;
    }

    public String songId() {
        return songId;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
