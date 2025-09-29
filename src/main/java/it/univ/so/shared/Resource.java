package it.univ.so.shared;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * Classe che rappresenta una risorsa gestita dal Peer.
 * - Include nome, contenuto
 * - Calcola automaticamente un checksum MD5 per verificarne integrità.
 */
public class Resource {
    private final String name;
    private final String content;
    private final String checksum;

    public Resource(String name, String content) {
        // Nome della risorsa
        this.name = name;
        // Contenuto testuale della risorsa
        this.content = content;
        // Impronta digitale MD% del contenuto
        this.checksum = calculateMD5();
    }

    /**
     * Calcola il checksum MD5 del contenuto della risorsa.
     * - Usa MessageDigest con algoritmo MD%
     * - Converte i bute in stringa esadecimale
     */
    private String calculateMD5() {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(content.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte hashByte : hashBytes) {
                String hex = Integer.toHexString(0xff & hashByte);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Errore nel calcolo del checksum: " + e.getMessage());
        }
    }

    // Getter
    public String getName() {
        return name;
    }

    public String getContent() {
        return content;
    }

    public String getChecksum() {
        return checksum;
    }
}
