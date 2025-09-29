package it.univ.so.peer;

import java.io.IOException;

/**
 * Classe di avvio del Peer.
 * Punto di ingresso dell'applicazione lato Peer:
 * - Controlla che i parametri da linea di comando siano corretti (IP + porta
 * Master)
 * - Verifica che la porta sia un numero valido (1024-65535)
 * - Inizializza il PeerEngine, che gestisce la logica vera e propria (Server +
 * REPL)
 */

public class PeerMain {

    public static void main(String[] args) {
        // Controllo degli argomenti: devono essere 2 → IP e porta del master
        if (args.length != 2) {
            System.out.println("Uso corretto: java PeerMain <master-ip> <master-port>");
            return;
        }

        String masterIp = args[0];
        int masterPort;

        try {
            // Parsing della porta
            masterPort = Integer.parseInt(args[1]);
            if (masterPort < 1024 || masterPort > 65535) {
                System.out.println("Errore: la porta deve essere compresa tra 1024 e 65535.");
                return;
            }
        } catch (NumberFormatException e) {
            // Caso in cui l'utente scrive una stringa non numerica
            System.out.println("Errore: la porta deve essere un numero intero.");
            return;
        }

        System.out.println("Avvio del Peer... Connessione al Master su " + masterIp + ":" + masterPort);

        try {
            // Inizializza il motore del Peer e avvia il ciclo interattivo
            PeerEngine engine = new PeerEngine(masterIp, masterPort);
            engine.start();
        } catch (IOException e) {
            System.err.println("Errore di connessione al Master: " + e.getMessage());
        }
    }
}
