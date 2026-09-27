package com.ultikits.plugins.mail.util;

/**
 * Fills the {@code {NAME}} placeholders of a message line.
 * <p>
 * Every line of this module that inserts more than one value into a template goes through
 * {@link #fill}, so there is one place that decides how the values are inserted.
 *
 * @author wisdomme
 * @version 1.0.0
 */
public final class Placeholders {

    private Placeholders() {
    }

    /**
     * Fills {@code template}'s placeholders.
     *
     * @param template       the message line, for example from a language file
     * @param namesAndValues pairs of a placeholder token (such as {@code "{FILE}"}) and its value
     * @return the filled line
     */
    public static String fill(String template, String... namesAndValues) {
        String out = template;
        for (int i = 0; i + 1 < namesAndValues.length; i += 2) {
            out = out.replace(namesAndValues[i], String.valueOf(namesAndValues[i + 1]));
        }
        return out;
    }
}
