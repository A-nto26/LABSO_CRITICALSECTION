SISTEMA DISTRIBUITO MASTER-PEER

Progetto sviluppato per il corso Laboratorio di Sistemi Operativi (LABSO) - A.A. 2024/25 presso l'università di Bologna

Descrizione

Sistema distribuito per la condivisione di file basato su un'archittettura Master-Peer
con funzionalità Peer-tp-Peer (P2P).

Il sistema è composto da:
+ un Master, che coordina la rete e mantiene la tabella globale delle risorse;
+ una rete di Peer, che si registrano presso il Master, ricercano le risorse
  disponibili e scambiano direttamente i file tra loro.

Il Master gestisce il coordinamento e i metadati, mentre il trasferimento dei file
avviene direttamente tra i Peer.

Tecnologie
+ Java 17
+ Maven
+ TCP Socket
+ Thread e programmazione concorrente
+ ReentrantLock
+ Strutture dati thread-safe
  
Funzionalità principali
+ Registrazione e gestione dei Peer
+ Pubblicazione e ricerca delle risorse
+ Download Peer-to-Peer
+ Gestione di connessioni concorrenti
+ Sincronizzazione delle risorse condivise
+ Download con logica round-robin
+ Configurazione tramite config.properties
+ Logging delle operazioni di download
  
Avvio/Compilazione

Il progetto utilizza Maven: mvn clean package

Il file .jar viene generato in: target/so-project-1.0-SNAPSHOT.jar

Avvio del Master
java -cp target/so-project-1.0-SNAPSHOT.jar it.univ.so.master.MasterMain 8000
Avvio di un Peer
java -cp target/so-project-1.0-SNAPSHOT.jar it.univ.so.peer.PeerMain 127.0.0.1 8000


Documentazione

Per informazioni dettagliate sull'architettura, sui componenti, 
sulla gestione della concorrenza e sincronizzazione, sulle funzionalità e sui test, 
consultare la relazione del progetto.

Sviluppo

Il progetto è stato realizzato in gruppo attraverso un approccio di peer programming, 
alternando i ruoli di driver e navigator e svolgendo attività di revisione condivisa del codice.
