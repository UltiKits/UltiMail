package com.ultikits.plugins.mail.commands;

/**
 * Free text whose final word may be a flag word, as in {@code /mail sendall <content> [items]} and
 * {@code /sendmail <player> <subject> [attach]}.
 *
 * <p>UltiTools-API takes a multi-word argument only as the last parameter ({@code <name...>}), so a separate
 * literal after a free-text parameter cannot be declared. The text is therefore collected whole and, when its
 * final word equals the flag word (ignoring letter case, as the command literal it replaces did), that word is
 * the flag and the words before it are the text. Only the final word counts: a flag word earlier in the text is
 * ordinary text. The documented consequence is that a text whose own last word is the flag word cannot be sent
 * without the flag (README, "Known limitations").
 */
final class TrailingFlag {

    private final String text;
    private final boolean flagged;

    private TrailingFlag(String text, boolean flagged) {
        this.text = text;
        this.flagged = flagged;
    }

    /**
     * Splits {@code words} into the text and whether the final word is {@code flag}.
     *
     * @param words the words of the free-text argument; {@code null} is treated as no words
     * @param flag  the flag word, for example {@code items}
     * @return the text (words joined by single spaces, possibly empty) and the flag
     */
    static TrailingFlag split(String[] words, String flag) {
        if (words == null || words.length == 0) {
            return new TrailingFlag("", false);
        }
        int end = words.length;
        boolean flagged = words[end - 1].equalsIgnoreCase(flag);
        if (flagged) {
            end--;
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < end; i++) {
            if (i > 0) {
                text.append(' ');
            }
            text.append(words[i]);
        }
        return new TrailingFlag(text.toString(), flagged);
    }

    /** @return the text before the flag word, words joined by single spaces; empty when there is none */
    String getText() {
        return text;
    }

    /** @return whether the final word was the flag word */
    boolean isFlagged() {
        return flagged;
    }
}
