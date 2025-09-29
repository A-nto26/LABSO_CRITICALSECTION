package it.univ.so.shared;

import java.time.LocalDateTime;

/**
 * Classe che rappresenta un log di download tra Peer.
 * - Include timestamp, nome della risorsa, peer sorgente, peer destinatario, e
 * esito.
 * - Usata principalmente da MasterCore per registrare i dowload
 */
public class NetworkRecord {

    // Quando e avvenuto il dowload
    private final LocalDateTime timestamp;
    // Nome della risorsa
    private final String resourceName;
    // Peer sorgente (chi ha fornito il file)
    private final String sourcePeer;
    // Peer destinatario (chi ha scaricato)
    private final String destinationPeer;
    // Esito del dowload (true/false)
    private final boolean successful;

    // Costruttore: crea un record e imposta automaticamente il timestamp
    public NetworkRecord(String resourceName, String sourcePeer,
            String destinationPeer, boolean successful) {
        // momento della creazione
        this.timestamp = LocalDateTime.now();
        this.resourceName = resourceName;
        this.sourcePeer = sourcePeer;
        this.destinationPeer = destinationPeer;
        this.successful = successful;
    }

    // Rappresentazione legibile del recordo (utile per stampa/log)
    @Override
    public String toString() {
        return String.format("%s: %s (%s → %s) %s",
                timestamp,
                resourceName,
                sourcePeer,
                destinationPeer,
                successful ? "SUCCESS" : "FAILED");
    }

    // Getter per accedere ai campi da MasterCore o altri moduli
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getResourceName() {
        return resourceName;
    }

    public String getSourcePeer() {
        return sourcePeer;
    }

    public String getDestinationPeer() {
        return destinationPeer;
    }

    public boolean isSuccessful() {
        return successful;
    }
}
