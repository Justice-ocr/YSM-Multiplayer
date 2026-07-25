package com.elfmcys.yesstevemodel.client;

import com.elfmcys.yesstevemodel.config.GeneralConfig;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ClientOnlyMode {

    private static final AtomicBoolean standalone = new AtomicBoolean();
    private static final AtomicBoolean catalogLoaded = new AtomicBoolean();

    private ClientOnlyMode() {
    }

    public static boolean isForced() {
        return GeneralConfig.FORCE_CLIENT_MODE != null && GeneralConfig.FORCE_CLIENT_MODE.get();
    }

    public static boolean isActive() {
        return isForced() || standalone.get();
    }

    public static void activateStandalone() {
        standalone.set(true);
        ClientModelManager.enterClientOnlyMode();
    }

    public static void leaveStandalone() {
        standalone.set(false);
    }

    public static boolean markCatalogLoaded() {
        return catalogLoaded.compareAndSet(false, true);
    }

    public static void reset() {
        standalone.set(false);
        catalogLoaded.set(false);
    }
}
