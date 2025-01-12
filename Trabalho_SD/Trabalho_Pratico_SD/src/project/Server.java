package project;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.locks.*;

public class Server {
    // Ajuste aqui conforme desejar o limite de sessões concorrentes
    public static final int MAX_SESSIONS = 2;

    // Utilizadores registados (username -> password)
    private static Map<String, String> users = new HashMap<>();

    // Armazém de dados (chave -> valor em bytes)
    private static Map<String, byte[]> dataStore = new HashMap<>();

    // Conjunto de clientes que estão, efetivamente, a ocupar uma sessão
    public static Set<ClientHandler> activeClients = new HashSet<>();

    // Fila de espera para garantir a ordem FIFO
    private static Queue<ClientHandler> waitingQueue = new LinkedList<>();

    // Lock e Condition para sincronizar o acesso
    public static Lock lock = new ReentrantLock();
    public static Condition sessionAvailable = lock.newCondition();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(12345)) {
            System.out.println("Servidor iniciado na porta 12345");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // -----------------------------------------------------------
    // Métodos de Gestão de Utilizadores
    // -----------------------------------------------------------
    public static boolean registerUser(String username, String password) {
        lock.lock();
        try {
            if (users.containsKey(username)) {
                return false;
            } else {
                users.put(username, password);
                return true;
            }
        } finally {
            lock.unlock();
        }
    }

    public static boolean authenticateUser(String username, String password) {
        lock.lock();
        try {
            return users.containsKey(username) && users.get(username).equals(password);
        } finally {
            lock.unlock();
        }
    }

    // -----------------------------------------------------------
    // Gestão de Sessões com Fila de Espera (FIFO)
    // -----------------------------------------------------------
    /**
     * Aguarda até que haja vaga E seja a vez deste cliente (o primeiro na fila).
     * 1) Adiciona o cliente à fila.
     * 2) Enquanto não houver vaga OU não for o primeiro da fila, fica bloqueado.
     * 3) Ao sair do while, remove da fila e informa o cliente que a sessão está disponível.
     */
    public static void waitForSessionAvailability(ClientHandler client)
            throws InterruptedException, IOException {

        lock.lock();
        try {
            // 1) Coloca o cliente na fila
            waitingQueue.add(client);

            // 2) Bloqueia enquanto:
            //    - O servidor estiver cheio (activeClients.size() >= MAX_SESSIONS)
            //    - OU este cliente NÃO for o primeiro da fila (waitingQueue.peek() != client)
            while (activeClients.size() >= MAX_SESSIONS
                    || waitingQueue.peek() != client) {

                // Envia uma mensagem avisando que está na fila de espera
                client.sendMessage("Aguardando vaga... Você está na fila.");
                sessionAvailable.await();
            }

            // Se saiu do while, significa:
            //  - Há vaga (activeClients.size() < MAX_SESSIONS)
            //  - Este cliente é o primeiro da fila (waitingQueue.peek() == client)

            waitingQueue.remove();
            client.sendMessage("Sessão disponível! Pode agora utilizar o serviço.");

        } finally {
            lock.unlock();
        }
    }

    public static void addActiveClient(ClientHandler client) {
        lock.lock();
        try {
            activeClients.add(client);
            System.out.println("Cliente " + client.getUsername()
                    + " entrou. Sessões ativas: " + activeClients.size());
        } finally {
            lock.unlock();
        }
    }

    public static void removeActiveClient(ClientHandler client) {
        lock.lock();
        try {
            activeClients.remove(client);
            System.out.println("Cliente " + client.getUsername()
                    + " saiu. Sessões ativas: " + activeClients.size());
            // Acorda todos os que estão em 'await()', para que verifiquem se agora é a sua vez
            sessionAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

    // -----------------------------------------------------------
    // Métodos de leitura/escrita no dataStore (put/get etc.)
    // -----------------------------------------------------------
    public static void put(String key, byte[] value) {
        lock.lock();
        try {
            dataStore.put(key, value);
        } finally {
            lock.unlock();
        }
    }

    public static byte[] get(String key) {
        lock.lock();
        try {
            return dataStore.getOrDefault(key, null);
        } finally {
            lock.unlock();
        }
    }

    public static void multiPut(Map<String, byte[]> pairs) {
        lock.lock();
        try {
            dataStore.putAll(pairs);
        } finally {
            lock.unlock();
        }
    }

    public static Map<String, byte[]> multiGet(Set<String> keys) {
        lock.lock();
        try {
            Map<String, byte[]> result = new HashMap<>();
            for (String key : keys) {
                if (dataStore.containsKey(key)) {
                    result.put(key, dataStore.get(key));
                }
            }
            return result;
        } finally {
            lock.unlock();
        }
    }
}
