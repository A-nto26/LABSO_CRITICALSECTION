package it.univ.so.peer;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * MasterClient gestisce la comunicazione tra il Peer e il Master.
 * Ogni metodo corrisponde a un'operazione che il Peer puo' fare verso il
 * Master:
 * - Registrazione
 * - Pubblicazione risorse
 * - Richiesta risorse
 * - Log dei dowload
 * - Lista risorse
 */
public class MasterClient {
    // Parametri di connessione al Master
    private final String masterHost;
    private final int masterPort;
    private final String peerId; // ID univoco del Peer

    // Socket e stream per la comunicazione
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    // Costruttore: salva host, porta e peerId
    public MasterClient(String masterHost, int masterPort, String peerId) {
        this.masterHost = masterHost;
        this.masterPort = masterPort;
        this.peerId = peerId;
    }

    /**
     * Connette il Peer al Master e lo registra con il comando REGISTER.
     * Usa REGISTER: <peerId> senza indicare la porta server del Peer.
     */
    public void connect() throws IOException {
        connect(0); // Metodo legacy senza porta server (chiama la versione estersa)
    }

    /**
     * Connette il Peer al Master e invia la registrazione.
     * Formati supportati:
     * - REGISTER: <peerId>
     * - REGISTER:<peerId>:<serverPort> (porta dal Server Peer per download P2P)
     */
    public void connect(int serverPort) throws IOException {
        socket = new Socket(masterHost, masterPort);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);

        // Costruisce il messaggio di registrazione
        String registerMessage = "REGISTER:" + peerId;
        if (serverPort > 0) {
            registerMessage += ":" + serverPort;
        }

        out.println(registerMessage);
        String response = in.readLine();

        if (!"REGISTERED".equals(response)) {
            throw new IOException("Registrazione fallita: " + response);
        }

        System.out.println("Registrazione al Master completata.");
    }

    /**
     * Chiede al Master quale Peer ha una certa risorsa.
     * LOOKUP:<resourceName>
     * Risposta: FOUND:<peerId>:<ip:port> oppure NOT_FOUND
     */

    public String requestResourceLocation(String resourceName) throws IOException {
        out.println("LOOKUP:" + resourceName);
        String response = in.readLine();

        if (response == null)
            return null;

        if (response.startsWith("FOUND:")) {
            return response.substring(6); // peerId:ip:port
        } else if (response.equals("NOT_FOUND") || response.equals("NO_PEER")) {
            return response; // Risposte speciali gestite dal PeerEngine
        }

        return null; // Risposta inattesa
    }

    /**
     * Pubblica una risorsa sul Master.
     * PUBLISH:<resourceName>
     */
    public void publishResource(String peerId, String resourceName) {
        out.println("PUBLISH:" + resourceName);
        String response;
        try {
            response = in.readLine();
            if (!"PUBLISHED".equals(response)) {
                System.err.println("Pubblicazione fallita: " + response);
            }
        } catch (IOException e) {
            System.err.println("Errore durante 'publish': " + e.getMessage());
        }
    }

    /**
     * Invia al Master il log di un download effettuato.
     * LOG:<resourceName>:<sourcePeerId>:<true/false>
     */

    public void logDownload(String resourceName, String sourcePeerId, String destinationPeerId, boolean success) {
        out.println("LOG:" + resourceName + ":" + sourcePeerId + ":" + success);
        try {
            String response = in.readLine();
            if (!"LOGGED".equals(response)) {
                System.err.println("Log non registrato: " + response);
            }
        } catch (IOException e) {
            System.err.println("Errore durante 'log': " + e.getMessage());
        }
    }

    /*
     * Informa il Master che un Peer indicato non ha più una risorsa.
     * Il Master aggiorna la sua tabella.
     * 
     * @param resourceName nome della risorsa
     * 
     * @param peerId id del Peer che non possiede più la risorsa
     */
    public void notifyDownloadFailure(String resourceName, String peerId) {
        out.println("DOWNLOADFAILED:" + peerId + ":" + resourceName);
        try {
            String response = in.readLine();
            if (!"ACK".equals(response)) {
                System.err.println("Fallimento non registrato: " + response);
            }
        } catch (IOException e) {
            System.err.println("Errore durante 'notifyDownloadFailure': " + e.getMessage());
        }
    }

    /**
     * Richiede al Master la lista di risorse disponibili in rete.
     * - LIST
     * Risposta: RESOURCES: Res1, Res2, ...
     */
    public List<String> requestResourceList() {
        List<String> results = new ArrayList<>();
        out.println("LIST");

        try {
            String response = in.readLine();

            if (response != null && response.startsWith("RESOURCES:")) {
                String resources = response.substring(10); // Rimuove "RESOURCES:"

                if (resources.equals("EMPTY")) {
                    return results; // Nessuna risorsa
                }

                String[] resourceArray = resources.split(";");
                for (String entry : resourceArray) {
                    String[] parts = entry.split(":");
                    if (parts.length >= 2) {
                        String resourceName = parts[0];
                        String peers = parts[1];
                        results.add(resourceName + " : " + peers);
                    } else if (parts.length == 1) {
                        // Caso: risorsa senza Peer associati
                        results.add(parts[0] + " : (nessun Peer)");
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Errore durante listdata remote: " + e.getMessage());
            e.printStackTrace();
        }

        return results;
    }

    /**
     * Chiude in sicurezza la connessione col Master.
     */
    public void close() {
        try {
            if (in != null)
                in.close();
            if (out != null)
                out.close();
            if (socket != null && !socket.isClosed())
                socket.close();
        } catch (IOException ignored) {
        }
    }
}
