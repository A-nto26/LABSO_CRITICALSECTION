package it.univ.so;

import it.univ.so.master.MasterMain;
import it.univ.so.peer.PeerMain;

/**
 * Classe di avvio generale dell'applicazione
 * Permette di lanciare il progetto in due modalità:
 * - Master
 * - Peer
 */

public class App {
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("master")) {
            // Avvia il master
            MasterMain.main(new String[0]);
        } else {
            // Avvia un peer (eventualmente passando gli altri argomenti)
            String[] peerArgs = args.length > 1 ? new String[] { args[1] } : new String[0];
            PeerMain.main(peerArgs);
        }
    }
}
