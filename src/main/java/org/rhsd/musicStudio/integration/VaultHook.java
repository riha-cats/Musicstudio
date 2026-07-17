package org.rhsd.musicStudio.integration;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

// =================================================================
// Vault economy 선택 연동
// =================================================================
// 돈으로 추출 비용을 뺄 때만 쓴다. compileOnly 라 런타임 의존이 아니며, Vault 타입은
// 이 클래스에만 갇혀 있고 economy 가 잡혔을 때만 건드린다. Vault 미설치 서버에서는
// setup 이 프로바이더 조회 전에 빠져나가 Economy 를 링크할 일이 없다
//
// Economy 는 플러그인이 아니라 Vault 가 중개하는 서비스다. 실제 구현(EssentialsX,
// CMI 등)이 ServicesManager 에 등록해 두므로 거기서 받아온다. Vault 만 깔고 돈 플러그인이
// 없으면 프로바이더가 없어 economy 가 null 로 남는다 (호출측이 이 상태를 구분해 처리)
public final class VaultHook {

    private final JavaPlugin plugin;
    private Economy economy;

    public VaultHook(JavaPlugin plugin) {
        this.plugin = plugin;
        setup();
    }

    private void setup() {
        // [1] :: Vault 자체가 없으면 여기서 끝. 아래 Economy 참조를 링크하지 않는다
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        // [2] :: 돈 플러그인이 등록한 economy 서비스 조회
        RegisteredServiceProvider<Economy> provider =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (provider == null) {
            plugin.getLogger().warning(
                    "Vault 는 있지만 economy 프로바이더가 없습니다 (EssentialsX 등 미설치). "
                            + "economy 비용은 사용할 수 없습니다.");
            return;
        }
        economy = provider.getProvider();
        plugin.getLogger().info("Vault economy 연동됨: " + economy.getName());
        // [STOP] :: 연동 준비 끝
    }

    // economy 로 비용을 뺄 수 있는 상태인가? (Vault + 프로바이더 둘 다 있어야 true)
    public boolean isAvailable() {
        return economy != null;
    }

    public boolean has(OfflinePlayer player, double amount) {
        return economy != null && economy.has(player, amount);
    }

    // 잔액에서 차감. 성공하면 true. 트랜잭션이 거부되면(잔액 부족 등) false
    public boolean withdraw(OfflinePlayer player, double amount) {
        if (economy == null) {
            return false;
        }
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }

    // 금액을 서버 통화 표기로. economy 가 없으면 숫자만 (표시용이라 실패해도 무해)
    public String format(double amount) {
        return economy != null ? economy.format(amount) : String.valueOf(amount);
    }
}
