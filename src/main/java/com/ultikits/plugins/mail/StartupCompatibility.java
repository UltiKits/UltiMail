package com.ultikits.plugins.mail;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure startup policy; unknown versions must never cause a guessed refusal. */
final class StartupCompatibility {
    enum Decision { ALLOW, REFUSE, UNKNOWN }

    private static final Pattern VERSION = Pattern.compile(
            "^(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:[-+][A-Za-z0-9][A-Za-z0-9.+-]*)?$");

    private StartupCompatibility() { }

    static Decision evaluate(String minecraft, String framework) {
        int[] server = parse(minecraft);
        int[] core = parse(framework);
        if (server == null || core == null) {
            return Decision.UNKNOWN;
        }
        return below(server, 1, 21) && below(core, 6, 4) ? Decision.REFUSE : Decision.ALLOW;
    }

    private static boolean below(int[] version, int major, int minor) {
        return version[0] < major || (version[0] == major && version[1] < minor);
    }

    private static int[] parse(String text) {
        if (text == null) {
            return null;
        }
        Matcher match = VERSION.matcher(text);
        if (!match.matches()) {
            return null;
        }
        try {
            return new int[] {Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)),
                    match.group(3) == null ? 0 : Integer.parseInt(match.group(3))};
        } catch (NumberFormatException invalid) {
            return null;
        }
    }
}
