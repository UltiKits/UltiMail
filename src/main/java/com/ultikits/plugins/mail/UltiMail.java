package com.ultikits.plugins.mail;

import com.ultikits.plugins.mail.listener.AttachmentGUIListener;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.UltiToolsModule;
import com.ultikits.ultitools.context.SimpleContainer;

/**
 * UltiMail - In-game mail system for Minecraft servers.
 * <p>
 * Features:
 * - Send mail to online/offline players
 * - Attach items to mail
 * - Mail notifications on join
 * - Mail history
 * </p>
 *
 * @author wisdomme
 * @version 1.1.0
 */
@UltiToolsModule(scanBasePackages = {"com.ultikits.plugins.mail"})
public class UltiMail extends UltiToolsPlugin {

    @Override
    public boolean registerSelf() {
        getLogger().info(i18n("UltiMail 已启用！"));
        return true;
    }

    /**
     * Hands back every item an attachment selector is still holding, before this module stops
     * listening.
     * <p>
     * An item placed in that GUI lives only in an in-memory inventory container until the mail is
     * created, and the two handlers that give it back both need an inventory-close or
     * player-quit event. Neither arrives when this module is unloaded: {@code /upm uninstall
     * UltiMail} unregisters its listeners while every player stays online, and on shutdown
     * {@code CraftServer#disablePlugins()} runs before the players are removed, so the listener is
     * gone by the time the close events fire. This hook is the one point that still has both the
     * tracking and the online player -- the framework calls it BEFORE unregistering this module's
     * listeners, and the player data it writes is saved afterwards
     * ({@code UltiKits/UltiMail#27}).
     * <p>
     * {@code unregisterSelf()} is deliberately NOT overridden: it is {@code final} in UltiTools
     * 6.3.0 and always runs the framework's own command and listener unregistration around this
     * hook. Overriding it is what {@code UltiKits/UltiMail#20} was about.
     */
    @Override
    protected void onUnregister() {
        SimpleContainer context = getContext();
        if (context == null) {
            // Reachable only for an instance that never went through PluginManager#register(...),
            // which therefore never opened a selector either.
            return;
        }
        AttachmentGUIListener listener = context.getBean(AttachmentGUIListener.class);
        if (listener != null) {
            listener.returnEveryOpenSelector();
        }
    }
}
