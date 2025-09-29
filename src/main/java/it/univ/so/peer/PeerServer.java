package it.univ.so.peer;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;
import it.univ.so.shared.Resource;

/**
 * PeerServer accetta connessioni da altri Peer e serve risorse locali tramite socket
 * - Viene eseguito in un thread separato (così non blocca il REPL utente).
 * - Gestisce richieste GET:<nome_risorse>.
 * - Restituisce DATA:<contenuto> oppure ERROR se non trovata.
 */
public class PeerServer implements Runnable {

    //Riferimento al motore del Peer (per accedere alle risorse locali)
    private final PeerEngine peerEngine;
    //Socket server del Peer
    private ServerSocket serverSocket;
    //Porta su cui ascolta
    private int port;
    //Flag di esecuzione
    private boolean running = true;

    //Costruttore: inizializza PeerServer e apre il socket
    public PeerServer(PeerEngine peerEngine) {
        this.peerEngine = peerEngine;
        //Carica porta da config o genera una random
        loadConfiguration();
        try {
            this.serverSocket = new ServerSocket(port);
            System.out.println("Server peer avviato sulla porta " + port);
        } catch (IOException e) {
            System.err.println("Errore durante l'avvio del Server Peer: " + e.getMessage());
        }
    }

    /** 
     * Carica la configurazione della porta da file config.properties.
     * se "peer.port" = 0 o mancante -> assegna una porta casuale tra 8001-9000 */
    private void loadConfiguration() {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream("src/main/resources/config.properties")) {
            props.load(fis);
            this.port = Integer.parseInt(props.getProperty("peer.port", "0"));
            if (this.port == 0) {
                this.port = 8001 + (int) (Math.random() * 1000);
            }
        } catch (IOException e) {
            System.err.println("Errore config: uso porta di fallback.");
            this.port = 8001 + (int) (Math.random() * 1000);
        }
    }

    /**
     * Metodo run(): ciclo principale del Server.
     * - Rimane in ascolto su serverSocket
     * - Per ogni connessione, avvia un nuovo thread che gestisce la richiesta.
     */

    @Override
public void run() {
    try {
        while (running) {
            Socket client = serverSocket.accept();

            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    handleRequest(client);
                }
            });
            t.start();
        }
    } catch (IOException e) {
        if (running) {
            System.err.println("Errore nel Server Peer: " + e.getMessage());
        }
    }
}


    /** 
     * Gestisce una richiesta di download da un altro Peer.
     * Mutua esclusione: "synchronized" assicura che venga servita una richiesta per volta.
     * Protocollo atteso: GET:<nome_risorsa>
     * Risposta: Data:<contenuto> oppure ERROR:NOT_FOUND
    */
    private synchronized void handleRequest(Socket socket) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))
        ) {
            String request = in.readLine();
            if (request != null && request.startsWith("GET:")) {
                String resourceName = request.substring(4);
                Resource res = peerEngine.getResourceManager().getResource(resourceName);

                if (res == null) {
                    out.write("ERROR:NOT_FOUND\n");
                } else {
                    out.write("DATA:" + res.getContent() + "\n");
                }
                out.flush();
            } else {
                out.write("ERROR:INVALID_COMMAND\n");
                out.flush();
            }

        } catch (IOException e) {
            System.err.println("Errore nella comunicazione col Peer richiedente: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    //Restituisce la porta su cui il PeerServer è in ascolto 
    public int getPort() {
        return this.port;
    }

    // Permette la chiusura controllata di PeerServer.
    public void stop() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
        }
    }
}