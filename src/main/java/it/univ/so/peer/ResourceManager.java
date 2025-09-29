package it.univ.so.peer;

import it.univ.so.shared.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ResourceManager gestisce le risorse locali del Peer in modo thread-safe.
 * - Permette l'aggiunta di nuove risorse
 * - Consente la ricerca e la lista di quelle esistenti
 * - Usa ConcurrentHasMap per garantire accesso sicuro in presenza di piu thread
 */
public class ResourceManager {

    // Mappa thread-safe: associa nome risorsa -> oggetto Resource
    private final ConcurrentHashMap<String, Resource> localResources = new ConcurrentHashMap<>();

    /**
     * Aggiunge una nuova risorsa al Peer 
     * Se una risorsa con lo stesso nome esiste, viene sovrascritta.
     * @param resource
     */
    public void addResource(Resource resource) {
        localResources.put(resource.getName(), resource);
    }

    /** 
     * Restituisce una risorsa locale, se esiste.
     * @param name nome della risorsa richiesta
     * @return la Resource oppure null se non trovata
     * */
    public Resource getResource(String name) {
        return localResources.get(name);
    }

    /**
     * Elenca i nomi delle risorse attualmente disponibili localmente.
     * Metodo usato dal comando `listdata local`.
     * @return lista di nomi delle risorse
     * */
    public List<String> listResources() {
        return new ArrayList<>(localResources.keySet());
    }

    /**
     * Verifica se una risorsa è presente localmente.
     @param name nome della risorsa
     @return true se esiste, false altrimenti
     */
    public boolean hasResource(String name) {
        return localResources.containsKey(name);
    }

    /**
     * Stampa tutte le risorse locali con un messaggio descrittivo.
     * Usato solo per debug: include anche il checlsum della risorsa.
     * 
     */
    public void listLocalResources() {
        if (localResources.isEmpty()) {
            System.out.println("Nessuna risorsa locale");
            return;
        }

        System.out.println("Risorse locali:");
        localResources.keySet().forEach(name -> {
            Resource res = localResources.get(name);
            System.out.println("- " + name + " [checksum: " + res.getChecksum() + "]");
        });
    }
}