package org.arcos.UserModel.GdeltThemeIndex;

public enum KeywordLanguage {
    FR("french"),
    EN("english"),
    GDELT_THEME(null);

    private final String gdeltSourceLang;

    KeywordLanguage(String gdeltSourceLang) {
        this.gdeltSourceLang = gdeltSourceLang;
    }

    public String getGdeltSourceLang() {
        return gdeltSourceLang;
    }
}
