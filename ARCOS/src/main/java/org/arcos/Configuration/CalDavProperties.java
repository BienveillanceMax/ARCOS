package org.arcos.Configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "arcos.calendar")
public class CalDavProperties {

    /** URL de base du serveur CalDAV (ex: http://localhost:5232) */
    private String url = "http://localhost:5232";

    /** Nom d'utilisateur CalDAV */
    private String username = "arcos";

    /** Mot de passe CalDAV */
    private String password = "arcos";

    /** Nom du calendrier dans Radicale */
    private String calendarName = "calendar";

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCalendarName() {
        return calendarName;
    }

    public void setCalendarName(String calendarName) {
        this.calendarName = calendarName;
    }

    /**
     * URL complète de la collection calendrier CalDAV.
     */
    public String getCalendarUrl() {
        return url + "/" + username + "/" + calendarName + "/";
    }
}
