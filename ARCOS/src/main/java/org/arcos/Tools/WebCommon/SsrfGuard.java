package org.arcos.Tools.WebCommon;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * SSRF guard : refuse les hôtes résolvant vers une adresse privée, loopback,
 * link-local, wildcard ou l'endpoint de métadonnées cloud (169.254.169.254).
 * Partagé entre la lecture de page web et le deep-search.
 */
@Component
public class SsrfGuard {

    private static final String METADATA_IP = "169.254.169.254";

    public void assertHostAllowed(URI uri) throws IOException {
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            // Per-hop scheme check — also keeps a redirect to ftp://, file:// etc. from escaping
            // as an UNCHECKED IllegalArgumentException out of HttpRequest.newBuilder(), which
            // the Actions' catch(IOException) would NOT map to an ActionResult.failure.
            throw new IOException("Hôte interdit : schéma non supporté : " + scheme);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IOException("Hôte interdit : URL sans hôte valide");
        }
        InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IOException("Hôte interdit : résolution DNS impossible pour " + host, e);
        }
        for (InetAddress addr : resolved) {
            if (addr.isLoopbackAddress()
                    || addr.isSiteLocalAddress()      // 10/8, 172.16/12, 192.168/16
                    || addr.isLinkLocalAddress()      // 169.254/16 (couvre aussi l'IP métadonnées)
                    || addr.isAnyLocalAddress()
                    || isCgnatOrUla(addr)             // 100.64/10 (CGNAT), fc00::/7 (IPv6 ULA)
                    || METADATA_IP.equals(addr.getHostAddress())) {
                throw new IOException("Hôte interdit (réseau privé/métadonnées) : "
                        + host + " -> " + addr.getHostAddress());
            }
        }
    }

    /** 100.64.0.0/10 (CGNAT) et fc00::/7 (IPv6 ULA) — non couverts par isSiteLocalAddress(). */
    private static boolean isCgnatOrUla(InetAddress addr) {
        byte[] b = addr.getAddress();
        if (b.length == 4) {
            return (b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 0x40;
        }
        return (b[0] & 0xFE) == 0xFC;
    }
}
