package com.ultikits.plugins.mail;

import com.ultikits.plugins.mail.config.ConfigTextDefaults;
import com.ultikits.plugins.mail.config.MailConfig;
import com.ultikits.plugins.mail.config.RemovedConfigKeys;
import com.ultikits.plugins.mail.listener.AttachmentGUIListener;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.UltiToolsModule;
import com.ultikits.ultitools.context.SimpleContainer;

import java.io.File;
import java.io.IOException;

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
        getLogger().info(i18n("mail_enabled"));
        // Deleting a key from MailConfig does nothing to the operator's existing file, so tell them
        // about any key this version no longer reads (UltiKits/UltiMail#23).
        warnAboutRemovedConfigKeys();
        writeConfigTextInServerLanguage();
        return true;
    }

    /**
     * Runs after the framework has re-read {@code mail.yml}, on every reload of this module -- a bare
     * {@code /ul reload} as well as {@code /ul reload UltiMail}. Repeats the removed-key warning, so
     * an operator who edits a key this version no longer reads and reloads is told it has no effect
     * (UltiKits/UltiMail#23). {@code reloadSelf()} itself is {@code final} and not overridden.
     */
    @Override
    protected void onReload() {
        warnAboutRemovedConfigKeys();
        writeConfigTextInServerLanguage();
    }

    /**
     * Writes every text setting in {@code config/mail.yml} that is still built-in text in the server's
     * language and saves the file once, so the file holds what the module shows; any other value is the
     * operator's and is kept (maintainer decision 2026-09-25, UltiKits/UltiMail#21, UltiKits/UltiMail#22).
     * Runs from {@link #registerSelf()} and from {@link #onReload()}, both after the module's language is
     * loaded -- never from a configuration change listener, which the framework fires before it reloads
     * the language. A value already in the current language matches nothing to replace, so a second
     * start writes nothing.
     * The text comes from this jar's own catalogue for the server's language, not from {@code i18n} (which
     * reads the operator's extracted language file first), so every value written is one the next pass
     * recognises (the text source decision of 2026-09-25).
     */
    private void writeConfigTextInServerLanguage() {
        MailConfig config = getConfig(MailConfig.class);
        if (config == null || !config.materializeText(
                ConfigTextDefaults.jarLanguage(MailConfig.class, getLanguageCode())::getLocalizedText)) {
            return;
        }
        try {
            config.save();
        } catch (IOException e) {
            getLogger().warn(e, i18n("log_config_default_save_failed").replace("{FILE}", MailConfig.CONFIG_FILE));
        }
    }

    private void warnAboutRemovedConfigKeys() {
        // Advisory only: nothing it throws may cost the module its enable or its reload.
        try {
            RemovedConfigKeys.warnAboutLeftovers(operatorConfigFile(), getLogger()::warn, this);
        } catch (RuntimeException e) {
            getLogger().warn(e, i18n("log_removed_key_check_failed").replace("{FILE}", MailConfig.CONFIG_FILE));
        }
    }

    /**
     * The path of this module's configuration file, relative to its folder -- read from
     * {@link MailConfig#CONFIG_FILE}, the same constant that entity binds, never a copy of it.
     * Package-private so a test can require the two to be equal.
     *
     * @return {@code config/mail.yml}
     */
    String operatorConfigPath() {
        return MailConfig.CONFIG_FILE;
    }

    /**
     * The operator's own copy of this module's configuration file.
     * <p>
     * A seam, package-private on purpose. {@code UltiToolsPlugin#getConfigFile} is {@code protected}
     * and {@code final}, so a test in this package can neither call it nor stub it, and a mocked
     * plugin returns {@code null} from it -- which means that without this method the removed-key
     * check's wiring could not be asserted at all, only its predicate.
     *
     * @return the file {@code config/mail.yml} resolves to for this installation
     */
    File operatorConfigFile() {
        return getConfigFile(operatorConfigPath());
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
