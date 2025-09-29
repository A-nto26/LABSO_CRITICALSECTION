package it.univ.so.master;

/**
 * Classe di avvio del Master.
 * Punto di ingresso in ingresso dell'applicazione lato Server:
 * - Verifica che la porta sia passata come argomento valido
 * - Controlla che sia un numero intero conpreso tra 1024 e 65535
 * - Avvia il MasterCore, che gestisce la logica vera e propria
 * 
 * @param args
 */

public class MasterMain {
    public static void main(String[] args) {
        // Controllo numero di argomenti
        if (args.length != 1) {
            System.out.println("Uso corretto: java MasterMain <porta>");
            return;
        }

        int port;
        try {
            //Parsing porta da stringa a intero
            port = Integer.parseInt(args[0]);

            //Controllo che la porta sia in un range valido 
            if (port < 1024 || port > 65535) {
                System.out.println("Errore: la porta deve essere compresa tra 1024 e 65535.");
                return;
            }
        } catch (NumberFormatException e) {
            //Caso in cui l'utente inserisce un valore non numerico
            System.out.println("Errore: la porta deve essere un numero intero.");
            return;
        }

        //Se tutto è valido, avvia il Master 
        System.out.println("Avvio del Server Master sulla porta " + port + "...");

        MasterCore core = new MasterCore(port);
        core.start(); // Avvia il Server
    }
}
