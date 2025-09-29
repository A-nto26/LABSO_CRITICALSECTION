package it.univ.so.master;

import java.io.IOException;
import java.net.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

import it.univ.so.shared.NetworkRecord;

/**
 * Classe principale lato Master: mantiene la tabella delle risorse,
 * registra i peer, gestisce richieste e log dei dowload.
 * Tutto l'accesso a strutture dati condvise è protetto con lock,
 * per rispettare la mutua esclusione richiesta.
 */

public class MasterCore {
    private final int port;

    // Tabella risorse: risorsa -> lista peer che la possiedono
    private final Map<String, List<String>> resourceTable = new HashMap<>();

    // Mappa peer registrati: peerID -> indirizzo (ip:porta)
    private final Map<String, String> peerAddresses = new HashMap<>();

    // Log dei dowload effettuati nella rete
    private final List<String> logs = new ArrayList<>();

    // Lock per garantire accesso esclusivo alle strutture dati
    private final Lock lock = new ReentrantLock();

    // Flag per mantenere attivo o meno il server
    private boolean running = true;

    // Costruttore: inizializza il Master sulla porta data
    public MasterCore(int port) {
        this.port = port;
    }

    /**
     * Metodo principale: avvia il server Master
     * - Apre un ServerSocket
     * - Gestisce nuove connessioni dai Peer tramite PeerHandler
     * - Avvia un thread REPL per i comandi da console
     */
    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Master avviato su porta " + port);

            ExecutorService pool = Executors.newCachedThreadPool();

            // Thread dedicato ai comandi da console (quit, log, listdata)
            new Thread(new Runnable() {
                @Override
                public void run() {
                    handleConsoleCommands();
                }
            }).start();

            // Ciclo principale di accettazione connessioni Peer
            while (running) {
                try {
                    // Timeout per permettere la chiusura ordinata
                    serverSocket.setSoTimeout(1000); // 1 secondo di timeout
                    Socket clientSocket = serverSocket.accept();

                    // Ogni Peer ha un suo PeerHandler eseguito in thread separato
                    pool.execute(new MasterHandler(clientSocket, this));
                } catch (SocketTimeoutException e) {
                    // Timeout normale, controlla se running è false
                    if (!running) {
                        break;
                    }
                    continue;
                } catch (SocketException e) {
                    // Errore di rete o chiusura volontaria del server
                    if (running) {
                        System.err.println("Errore del Server: " + e.getMessage());
                    }
                    break; // Chiusura volontaria (es. comando quit)
                }
            }

            // Chiusura ordinata: ferma tutti i thread attivi
            System.out.println("Chiusura ordinata del Master...");
            pool.shutdown();
            try {
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
            }
            System.out.println("Master terminato.");
        } catch (IOException e) {
            System.err.println("Errore durante l'avvio del Server: " + e.getMessage());
        }
    }

    /**
     * Gestione dei comandi da console
     * Comandi supportati:
     * - quit -> spegne il master
     * - log -> stampa il registro dei dowload
     * - listdata -> mostra tutte le risorse pubblicate dai peer
     */

    private void handleConsoleCommands() {
        Scanner scanner = new Scanner(System.in);
        try {
            while (running) {
                String cmd = scanner.nextLine().trim();
                switch (cmd) {
                    case "quit":
                        System.out.println("Avvio chiusura del Master...");
                        running = false;
                        break;
                    case "log":
                        synchronized (logs) {
                            if (logs.isEmpty()) {
                                System.out.println("Nessun download registrato.");
                            } else {
                                System.out.println("Log dei download:");
                                for (String log : logs) {
                                    System.out.println("- " + log);
                                }
                            }
                        }
                        break;
                    case "listdata":
                        lock.lock();
                        try {
                            if (resourceTable.isEmpty()) {
                                System.out.println("Nessuna risorsa disponibile nella rete.");
                            } else {
                                System.out.println("Risorse disponibili nella rete:");
                                for (Map.Entry<String, List<String>> entry : resourceTable.entrySet()) {
                                    System.out.println(
                                            "- " + entry.getKey() + ": " + String.join(", ", entry.getValue()));
                                }
                            }
                        } finally {
                            lock.unlock();
                        }
                        break;
                    default:
                        System.out.println("Comando non riconosciuto.");
                }
            }
        } finally {
            scanner.close();
        }
    }

    // ----------------------------------
    // Metodi di gestione Peer e risorse
    // ----------------------------------

    // Registra un Peer nel sistema
    // Si salva l'ID del Peer con il relativo indirizzo IP:porta
    public void registerPeer(String peerId, String address) {
        lock.lock();
        try {
            peerAddresses.put(peerId, address);
        } finally {
            lock.unlock();
        }
        System.out.println("Registrato Peer " + peerId + " @ " + address);
    }

    // Restituisce un Peer che possiede la risorsa richiesta in modalità round-robin
    public String getPeerForResource(String resourceName) {
        lock.lock();
        try {
            List<String> peers = resourceTable.get(resourceName);
            if (peers != null && !peers.isEmpty()) {
                // Prende il primo peer della lista
                String chosen = peers.remove(0);
                // Lo rimette in fondo alla lista -> round-robin
                peers.add(chosen);
                return chosen;
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    // Restituisce l'indirizzo (IP:PORTA) di un peer registrato
    public String getPeerAddress(String peerId) {
        lock.lock();
        try {
            return peerAddresses.get(peerId);
        } finally {
            lock.unlock();
        }
    }

    // Aggiunge una risorsa al Peer
    public void addResourceToPeer(String resourceName, String peerId) {
        lock.lock();
        try {
            // Controlla se la risorsa esiste già nella tabella, altrimenti la crea
            if (!resourceTable.containsKey(resourceName)) {
                resourceTable.put(resourceName, new ArrayList<String>());
            }

            List<String> peers = resourceTable.get(resourceName);
            if (!peers.contains(peerId)) {
                peers.add(peerId);
            }
        } finally {
            lock.unlock();
        }
        System.out.println(" +  " + peerId + " pubblica risorsa \"" + resourceName + "\"");
    }

    // Rimuove una risorsa da un Peer
    // Rimuove una risorsa da un Peer
    public void removeResourceFromPeer(String resourceName, String peerId) {
        lock.lock();
        try {
            List<String> peers = resourceTable.get(resourceName);

            // caso: risorsa non esiste nella tabella
            if (peers == null) {
                return;
            }

            peers.remove(peerId);

            // Se non ci sono Peer per la risorsa, rimuoviamo la risorsa dalla tabella
            if (peers.isEmpty()) {
                resourceTable.remove(resourceName);

                // Stampiamo solo se era una risorsa registrara
                if (!"NO_SOURCE".equals(peerId)) {
                    System.out.println("Risorsa \"" + resourceName +
                            "\" rimossa dal Master (nessun Peer disponibile).");
                }
            } else {
                // Caso normale: altri peer hanno ancora la risorsa
                if (!"NO_SOURCE".equals(peerId) && !"NO_AVAILABLE_PEER".equals(peerId)) {
                    System.out.println("Rimosso Peer " + peerId +
                            " dalla risorsa \"" + resourceName + "\"");
                }
            }

        } finally {
            lock.unlock();
        }

        // Caso normale: c’era ancora almeno un peer con la risorsa
        if (!"NO_SOURCE".equals(peerId) && !"NO_AVAILABLE_PEER".equals(peerId)) {
            System.out.println("Rimosso Peer " + peerId + " dalla risorsa \"" + resourceName + "\"");
        }
    }

    // Rimuove un Peer dal sistema (ad esempio se si disconnette)
    public void removePeer(String peerId) {
        lock.lock();
        try {
            peerAddresses.remove(peerId);

            // Raccoglie le risorse da eliminare
            List<String> toRemove = new ArrayList<>();

            for (Map.Entry<String, List<String>> entry : resourceTable.entrySet()) {
                List<String> peers = entry.getValue();
                peers.remove(peerId);

                // Se la risorsa non ha più Peer, segna da eliminare
                if (peers.isEmpty()) {
                    toRemove.add(entry.getKey());
                }
            }

            // Rimuove effettivamente le risorse
            for (String res : toRemove) {
                resourceTable.remove(res);
                System.out.println("Risorsa \"" + res + "\" rimossa dal Master.");
            }

        } finally {
            lock.unlock();
        }

        System.out.println("Peer disconnesso: " + peerId);
    }

    // Aggiunge un record di dowload al log
    public void logDownload(NetworkRecord record) {
        String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        String msg;

        if (record.getSourcePeer().equals("NO_SOURCE")) {
            msg = timestamp + " Tentativo fallito: la risorsa \"" +
                    record.getResourceName() + "\" non esiste. Richiedente: " +
                    record.getDestinationPeer();
        } else if (record.getSourcePeer().equals("NO_AVAILABLE_PEER")) {
            msg = timestamp + " Tentativo fallito: la risorsa \"" +
                    record.getResourceName() + "\" non e' più disponibile in rete. Richiedente: " +
                    record.getDestinationPeer();
        } else {
            msg = timestamp + " " + record.getResourceName() +
                    " da: " + record.getSourcePeer() +
                    " a: " + record.getDestinationPeer() +
                    (record.isSuccessful() ? " SUCCESS" : " FAILED");
        }

        synchronized (logs) {
            logs.add(msg);
        }
        System.out.println("LOG: " + msg);
    }

    /**
     * Restituisce una copia della tabella risorse (risorsa -> lista di Peers)
     * Serve per LIST, cosi' il Master puo' comunicare anche i Peer che possiedono
     * ciascuna risorsa.
     */
    public Map<String, List<String>> getResourceTable() {
        lock.lock();
        try {
            Map<String, List<String>> copy = new HashMap<>();
            for (Map.Entry<String, List<String>> entry : resourceTable.entrySet()) {
                copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            return copy;
        } finally {
            lock.unlock();
        }
    }
}
