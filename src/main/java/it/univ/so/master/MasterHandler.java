package it.univ.so.master;

import it.univ.so.shared.NetworkRecord;
import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.Map;

/**
 * PeerHandler gestisce la comunicazione tra il Master e un singolo Peer.
 * Ogni connessione da un Peer viene gestita da un thread PeerHandler dedicato.
 * 
 * Si occupa di:
 * - Registrare il peer;
 * - Ricevere e processare i comandi dal peer (LOOKUP, PUBLISH, LOG, LIST);
 * - Gestire la chiusura della connessione e la rimozione del peer.
 */
public class MasterHandler implements Runnable {
    private final Socket clientSocket; // Socket della connessione con il Peer
    private final MasterCore masterCore; // Riferimento al masterCore centrale
    private BufferedReader in; // Lettore input dal peer
    private PrintWriter out; // Scrittore output verso il peer
    private String peerId; // ID del Peer connesso

    // Costruttore: inizializza la connessione e il riferimento al Master.
    public MasterHandler(Socket socket, MasterCore masterCore) {
        this.clientSocket = socket;
        this.masterCore = masterCore;
    }

    /**
     * Metodo run(): entry point del thread
     * 1. Attende la registrazione del Peer (REGISTER)
     * 2. Cicla in ascolto dei comandi inviati dal Peer
     */
    @Override
    public void run() {
        String clientAddress = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
        System.out.println("Nuova connessione da: " + clientAddress);

        try {
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            // 1. Registrazione del Peer
            String firstMessage = in.readLine();
            if (firstMessage != null && firstMessage.startsWith("REGISTER:")) {
                String[] registerParts = firstMessage.substring(9).split(":");
                peerId = registerParts[0];

                String peerAddress;
                if (registerParts.length > 1) {
                    // Formato esteso: REGISTER: <peerId>:<serverPort>
                    // Usa la porta del server fornita dal Peer (serve per i dowload P2P)
                    int serverPort = Integer.parseInt(registerParts[1]);
                    peerAddress = clientSocket.getInetAddress().getHostAddress() + ":" + serverPort;
                } else {
                    // Formato base: REGISTER:<peerId>
                    // Usa la porta della connessione corrente (legacy)
                    peerAddress = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
                }

                masterCore.registerPeer(peerId, peerAddress); // aggiorna tabella Peer
                out.println("REGISTERED");
                System.out.println("Peer " + peerId + " registrato da " + clientAddress);
            } else {
                // Peer non si è registrato -> rifiutato
                System.out.println("Registrazione fallita da " + clientAddress + ": messaggio non valido");
                out.println("ERROR:REGISTRATION_REQUIRED");
                closeConnection();
                return;
            }

            // 2. Ciclo principale comandi
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                processCommand(inputLine);
            }
        } catch (IOException e) {
            if (peerId != null) {
                System.err
                        .println("Errore nella gestione della connessione del Peer " + peerId + ": " + e.getMessage());
            } else {
                System.err
                        .println("Errore nella gestione della connessione da " + clientAddress + ": " + e.getMessage());
            }
        } finally {
            closeConnection();
        }
    }

    /*
     * processCommand(): gestisce i comandi inviati dal peer.
     * Comandi supportati:
     * - LOOKUP:<risorsa> → trova un peer che ha quella risorsa
     * - PUBLISH:<risorsa> → pubblica una nuova risorsa
     * - LOG:<risorsa>:<sourcePeer>:<true/false> → log di un download
     * - LIST → restituisce elenco risorse disponibili
     */
    private void processCommand(String command) {
        String[] parts = command.split(":", 2);

        switch (parts[0]) {

            case "LOOKUP":
                // Cerca una risorsa nella tabella
                if (parts.length < 2) {
                    out.println("ERROR:INVALID_COMMAND");
                    break;
                }
                String resourceName = parts[1];

                // Caso: risorsa mai pubblicata
                if (!masterCore.getResourceTable().containsKey(resourceName)) {
                    out.println("NOT_FOUND"); // Risorsa inesistente
                    break;
                }

                // Caso: risorsa esiste ma nessun Peer disponibile
                String sourcePeer = masterCore.getPeerForResource(resourceName);
                if (sourcePeer == null) {
                    out.println("NO_PEER");
                } else {
                    String peerAddress = masterCore.getPeerAddress(sourcePeer);
                    out.println("FOUND:" + sourcePeer + ":" + peerAddress);
                }
                break;

            case "PUBLISH":
                // Pubblica una nuova risorsa

                if (parts.length < 2) {
                    out.println("ERROR:INVALID_COMMAND");
                    break;
                }
                String resourceToPublish = parts[1];
                masterCore.addResourceToPeer(resourceToPublish, peerId);
                out.println("PUBLISHED");
                break;

            case "LOG":
                // Il Peer comunica l’esito di un download
                // Formato: LOG:<resource>:<sourcePeer>:<true/false>
                if (parts.length < 2) {
                    out.println("ERROR:INVALID_COMMAND");
                    break;
                }
                String[] logParts = parts[1].split(":");
                if (logParts.length >= 3) {
                    String logResourceName = logParts[0];
                    String sourcePeerId = logParts[1];
                    boolean success = Boolean.parseBoolean(logParts[2]);

                    // Registra il log nel sistema centrale (nel Master)
                    NetworkRecord record = new NetworkRecord(
                            logResourceName, sourcePeerId, peerId, success);
                    masterCore.logDownload(record);
                    out.println("LOGGED");

                    // Se il download è fallito, aggiorna la tabella rimuovendo il Peer fallito
                    if (!success) {
                        masterCore.removeResourceFromPeer(logResourceName, sourcePeerId);
                    }
                } else {
                    out.println("ERROR:INVALID_LOG_FORMAT");
                }
                break;

            case "LIST":
                // Costruisce una risposta con risorse e Peer che la possiedono
                StringBuilder response = new StringBuilder("RESOURCES:");
                for (Map.Entry<String, List<String>> entry : masterCore.getResourceTable().entrySet()) {
                    response.append(entry.getKey()) // nome risorsa
                            .append(":")
                            .append(String.join(",", entry.getValue())) // lista Peer
                            .append(";");
                }
                // Rimuove l'ultimo ";" se presente
                if (response.charAt(response.length() - 1) == ';') {
                    response.deleteCharAt(response.length() - 1);
                }
                out.println(response.toString());
                break;

            case "DOWNLOADFAILED":
                // Formato: DOWNLOADFAILED:<peerId>:<resourceName>
                if (parts.length < 2) {
                    out.println("ERROR:INVALID_COMMAND");
                    break;
                }
                String[] failParts = parts[1].split(":");
                if (failParts.length == 2) {
                    String failedPeerId = failParts[0];
                    String failedResource = failParts[1];

                    // Rimuove la risorsa dal Peer segnalato
                    masterCore.removeResourceFromPeer(failedResource, failedPeerId);

                    System.out.println("⚠️ Risorsa " + failedResource + " rimossa da " + failedPeerId
                            + " (segnalata da " + peerId + ")");
                    out.println("ACK");
                } else {
                    out.println("ERROR:INVALID_FAIL_FORMAT");
                }
                break;
            default:
                out.println("ERROR:UNKNOWN_COMMAND");
                break;
        }
    }

    /**
     * closeConnection(): chiude la connessione col Peer e aggiorna il Master
     * - Rimuove il Peer dalla tabella
     * - Chiude
     */
    private void closeConnection() {
        try {
            // Alla disconnessione, il master rimuove il peer dalla rete
            if (peerId != null) {
                masterCore.removePeer(peerId);
                System.out.println("Connessione Socket chiusa per Peer: " + peerId);
            } else {
                // Connessione chiusa prima della registrazione (probabilmente chiusura del
                // master)
                System.out.println("Connessione chiusa senza registrazione");
            }

            if (in != null)
                in.close();
            if (out != null)
                out.close();
            if (!clientSocket.isClosed())
                clientSocket.close();

        } catch (IOException e) {
            // Ignora errori di chiusura durante la terminazione del master
            if (peerId != null) {
                System.err.println(
                        "Errore durante la chiusura della connessione del Peer " + peerId + ": " + e.getMessage());
            }
        }
    }
}
