package it.univ.so.peer;

import it.univ.so.shared.Resource;
import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.HashSet;

/**
 * PeerEngine gestisce la logica principale del Peer:
 * - Connessione e registrazione al Master
 * - Gestione delle risorse locali
 * - Ciclo REPL per i comandi dell'utente
 * - Download di risorse da altri Peer
 */
public class PeerEngine {
    private final String peerId; // ID univoco del Peer
    private final ResourceManager resourceManager; // Gestore risorse locali (aggiunta/lista)
    private final MasterClient masterClient; // Client per comunicare col Master
    private final Scanner scanner = new Scanner(System.in); // Lettore input utente

    /**
     * Costruttore principale: crea un Peer e lo collega al Master.
     * 
     * @param masterIp   indirizzo IP del master
     * @param masterPort porta del master
     */
    public PeerEngine(String masterIp, int masterPort) {
        this.peerId = "Peer-" + (int) (Math.random() * 10000);
        this.masterClient = new MasterClient(masterIp, masterPort, peerId);
        this.resourceManager = new ResourceManager();
    }

    // Costruttore alternativo (per test): usa un MasterClient già pronto.
    public PeerEngine(String peerId, MasterClient masterClient) {
        this.peerId = peerId;
        this.masterClient = masterClient;
        this.resourceManager = new ResourceManager();
    }

    /**
     * Avvia il Peer(REPL):
     * 1. Avvia un server interno (PeerServer) per rispondere ai download degli
     * altri Peer
     * 2. Si registra al Master con il comando REGISTER
     * 3. Avvia il REPL (prompt) per gestire i comandi da console
     */
    public void start() throws IOException {
        // Avvio server per gestire richieste in entrata (GET)
        PeerServer peerServer = new PeerServer(this);

        // Avvio thread esplicito senza lambda
        Thread serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                peerServer.run();
            }
        });
        serverThread.start();

        // Attende un po' per dare tempo al Server di avviarsi
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Registrazione al Master -> REGISTER: <peerId>:<portaPeerServer>
        masterClient.connect(peerServer.getPort());

        System.out.println("Peer ID: " + peerId);
        System.out.println("Connesso al Master. In attesa di comandi...");

        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("quit")) {
                // Chiusura ordinata del Peer
                System.out.println("Chiusura del Peer...");
                masterClient.close();
                break;

            } else if (input.equalsIgnoreCase("listdata local")) {
                // Mostra risorse locali
                List<String> local = resourceManager.listResources();
                if (local.isEmpty()) {
                    System.out.println("Nessuna risorsa locale.");
                } else {
                    for (String res : local) {
                        System.out.println("- " + res);
                    }
                }

            } else if (input.equalsIgnoreCase("listdata remote")) {
                // Chiede al Master la lista delle risorse disponibili
                List<String> remoteResources = masterClient.requestResourceList();
                if (remoteResources.isEmpty()) {
                    System.out.println("Nessuna risorsa disponibile nella rete.");
                } else {
                    System.out.println("Risorse disponibili nella rete:");
                    for (String res : remoteResources) {
                        System.out.println("- " + res);
                    }
                }

            } else if (input.startsWith("add ")) {
                String[] parts = input.split(" ", 3);
                if (parts.length < 3) {
                    System.out.println("Uso: add <nome_risorsa> <contenuto>");
                    continue;
                }
                String name = parts[1];
                String content = parts[2];
                resourceManager.addResource(new Resource(name, content));
                masterClient.publishResource(peerId, name);
                System.out.println("Risorsa aggiunta: " + name);

            } else if (input.startsWith("download ")) {
                String[] parts = input.split(" ", 2);
                if (parts.length != 2) {
                    System.out.println("Uso: download <nome_risorsa>");
                    continue;
                }
                downloadResource(parts[1]);

            } else {
                System.out.println("Comando sconosciuto.");
            }
        }
    }

    /**
     * Gestisce il download di una risorsa dalla rete.
     * - Controlla se è già locale
     * - Chiede al Master quale Peer la possiede (LOOKUP)
     * - Tenta la connessione al Peer sorgente (GET)
     * - Ritenta fino a n volte in caso di errore
     * - Logga il risultato al Master (LOG true/false)
     */
    public void downloadResource(String resourceName) {
        // Se gia' locale, esce
        if (resourceManager.hasResource(resourceName)) {
            System.out.println("Risorsa " + resourceName + " già disponibile localmente.");
            return;
        }

        Set<String> triedPeers = new HashSet<>(); // Peer già provati
        int attempts = 0;

        while (true) {
            String sourcePeerInfo;
            try {
                sourcePeerInfo = masterClient.requestResourceLocation(resourceName);
            } catch (IOException e) {
                System.out.println("Errore durante la richiesta al Master: " + e.getMessage());
                break; // errore critico: usciamo
            }

            // Caso: risorsa inesistente
            if ("NOT_FOUND".equals(sourcePeerInfo)) {
                System.out.println("Tentativo fallito per il download di " + resourceName +
                        " per il richiedente: " + peerId + ". Risorsa inesistente.");
                masterClient.logDownload(resourceName, "NO_SOURCE ", peerId, false);
                return;
            }

            // Caso: risorsa esiste ma nessun Peer disponibile
            if ("NO_PEER".equals(sourcePeerInfo)) {
                System.out.println("Tentativo fallito per il download di " + resourceName +
                        " per il Peer: " + peerId + ". Risorsa non disponibile in rete.");
                masterClient.logDownload(resourceName, "NO_SOURCE ", peerId, false);
                return;
            }

            // Caso: risposta nulla dal Master
            if (sourcePeerInfo == null) {
                System.out
                        .println("Nessuna risposta dal Master: impossibile scaricare la risorsa " + resourceName + ".");
                masterClient.logDownload(resourceName, "NO_SOURCE ", peerId, false);
                return;
            }

            // Formato atteso "peerId:ip:port"
            String[] parts = sourcePeerInfo.split(":");
            if (parts.length != 3) {
                System.out.println("Formato Peer non valido restituito dal Master.");
                continue; // il Master riproverà con un altro Peer
            }

            String sourcePeerId = parts[0];
            String ip = parts[1];
            int port = Integer.parseInt(parts[2]);

            // Evita di scaricare da sé stessi
            if (sourcePeerId.equals(peerId)) {
                System.out.println("Risorsa " + resourceName +
                        " già disponibile localmente (indicata dal Master).");
                return;
            }

            // Evita Peer già provati
            if (triedPeers.contains(sourcePeerId)) {
                System.out.println("Esaurite le sorgenti disponibili per " + resourceName + ".");
                masterClient.logDownload(resourceName, "NO_SOURCE ", peerId, false);
                return;
            }

            // Tentativo effettivo di download
            attempts++;
            System.out.printf("Tentativo %d: download di %s da %s\n",
                    attempts, resourceName, sourcePeerId);

            boolean success = attemptDownload(sourcePeerId, ip, port, resourceName);

            if (success) {
                masterClient.logDownload(resourceName, sourcePeerId, peerId, true);
                System.out.println("Download completato da " + sourcePeerId + ".");
                return;
            } else {
                System.out.println("Download fallito da " + sourcePeerId + ".");
                masterClient.logDownload(resourceName, sourcePeerId, peerId, false);
                masterClient.notifyDownloadFailure(resourceName, sourcePeerId);
                triedPeers.add(sourcePeerId);
                // torna al while: il Master con round-robin darà un altro Peer
            }
        }

        // Fallback (caso inatteso)
        System.out.println("Impossibile completare il download di " + resourceName + ".");
        masterClient.logDownload(resourceName, "UNKNOWN", peerId, false);
    }

    /**
     * Esegue il download diretto da un Peer remoto.
     * - Invia "GET:<resourceName>"
     * - Riceve "DATA:<contenuto>"
     * - Salva la risorsa localmente
     */
    private boolean attemptDownload(String sourcePeerId, String ip, int port, String resourceName) {
        try (
                Socket socket = new Socket(ip, port);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("GET:" + resourceName);
            String response = in.readLine();

            if (response != null && response.startsWith("DATA:")) {
                String content = response.substring(5);
                resourceManager.addResource(new Resource(resourceName, content));
                return true;
            }
        } catch (IOException e) {
            System.err.println("Errore di comunicazione con il Peer " + sourcePeerId + ": " + e.getMessage());
            e.printStackTrace(); // Si aggiunge lo stack trace per debug
        }

        return false;
    }

    /*
     * Restituisce il ResourceManager associato a questo Peer.
     * 
     * @return resourceManager
     */
    public ResourceManager getResourceManager() {
        return resourceManager;
    }
}
