package kote.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class KoteClient implements ClientModInitializer {
    private static KeyMapping menuKey;
    private static KeyMapping zoomKey;

    private static boolean zooming = false;
    private static int savedFov = 70;
    private static Double savedGamma = null;

    @Override
    public void onInitializeClient() {
        KoteConfig.load();
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("koteclient", "main"));
        menuKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.koteclient.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
        zoomKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.koteclient.zoom", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, category));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (menuKey.consumeClick()) {
                if (mc.screen == null) mc.setScreen(new KoteScreen());
            }

            // Zoom: временно уменьшаем угол обзора, пока зажата клавиша
            if (mc.player != null) {
                boolean want = KoteConfig.ZOOM.enabled && zoomKey.isDown() && mc.screen == null;
                if (want) {
                    if (!zooming) { savedFov = mc.options.fov().get(); zooming = true; }
                    mc.options.fov().set((int) KoteConfig.ZOOM.setting("fov").value);
                } else if (zooming) {
                    mc.options.fov().set(savedFov);
                    zooming = false;
                }
            }

            // Максимальная яркость (ванильный максимум настройки яркости)
            if (KoteConfig.BRIGHTNESS.enabled) {
                if (savedGamma == null) savedGamma = mc.options.gamma().get();
                if (mc.options.gamma().get() < 1.0) mc.options.gamma().set(1.0);
            } else if (savedGamma != null) {
                mc.options.gamma().set(savedGamma);
                savedGamma = null;
            }
        });

        // Свой прицел заменяет ванильный только когда модуль включён
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (graphics, tickCounter) -> {
            if (KoteConfig.CROSSHAIR.enabled) KoteHud.crosshair(graphics);
            else original.render(graphics, tickCounter);
        });

        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
                Identifier.fromNamespaceAndPath("koteclient", "hud"),
                (graphics, tickCounter) -> KoteHud.render(graphics));
    }
}
