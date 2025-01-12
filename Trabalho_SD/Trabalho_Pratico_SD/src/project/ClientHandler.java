package project;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class ClientHandler implements Runnable {
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String username;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public String getUsername() {
        return this.username;
    }

    @Override
    public void run() {
        try {
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            // 1) Autenticação
            if (!authenticate()) {
                socket.close();
                return;
            }

            // 2) Esperar até ter vaga (ordem FIFO)
            Server.waitForSessionAvailability(this);

            // 3) Finalmente ocupar a sessão
            Server.addActiveClient(this);

            // 4) Loop principal de comandos
            boolean running = true;
            while (running) {
                String command;
                try {
                    command = in.readUTF();
                } catch (EOFException e) {
                    // Cliente fechou a conexão abruptamente
                    break;
                }

                switch (command) {
                    case "put":
                        handlePut();
                        break;
                    case "get":
                        handleGet();
                        break;
                    case "multiPut":
                        handleMultiPut();
                        break;
                    case "multiGet":
                        handleMultiGet();
                        break;
                    case "logout":
                        running = false;
                        break;
                    default:
                        sendMessage("Comando inválido.");
                }
            }

            // Ao sair do loop, remover da lista de ativos
            Server.removeActiveClient(this);
            socket.close();

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            // Se der erro, remover também da lista
            Server.removeActiveClient(this);
        }
    }

    /**
     * Método de autenticação: pergunta username e password;
     * se não existir, pergunta se deseja registar.
     */
    private boolean authenticate() throws IOException {
        sendMessage("Insira o seu nome de utilizador:");
        username = in.readUTF();
        sendMessage("Insira a sua palavra-passe:");
        String password = in.readUTF();

        if (Server.authenticateUser(username, password)) {
            sendMessage("Autenticação bem-sucedida.");
            return true;
        } else {
            sendMessage("Utilizador não encontrado. Deseja registar-se? (sim/nao)");
            String response = in.readUTF();
            if (response.equalsIgnoreCase("sim")) {
                boolean success = Server.registerUser(username, password);
                if (success) {
                    sendMessage("Registo bem-sucedido.");
                    return true;
                } else {
                    sendMessage("Registo falhado. Nome de utilizador já existe.");
                    return false;
                }
            } else {
                sendMessage("Autenticação falhada.");
                return false;
            }
        }
    }

    // ---------------------------------------------------------
    // Método util para enviar mensagem
    // ---------------------------------------------------------
    public void sendMessage(String message) throws IOException {
        out.writeUTF(message);
        out.flush();
    }

    // ---------------------------------------------------------
    // Handlers de cada comando
    // ---------------------------------------------------------
    private void handlePut() throws IOException {
        String key = in.readUTF();
        int length = in.readInt();
        if (length < 0) {
            sendMessage("Comprimento do valor inválido.");
            return;
        }
        byte[] value = new byte[length];
        in.readFully(value);
        Server.put(key, value);
        sendMessage("Chave-valor inseridos/atualizados com sucesso.");
    }

    private void handleGet() throws IOException {
        String key = in.readUTF();
        byte[] value = Server.get(key);
        if (value != null) {
            out.writeInt(value.length);
            out.write(value);
        } else {
            out.writeInt(0);
        }
        out.flush();
    }

    private void handleMultiPut() throws IOException {
        int numPairs = in.readInt();
        if (numPairs <= 0) {
            sendMessage("Número de pares inválido.");
            return;
        }
        Map<String, byte[]> pairs = new HashMap<>();
        for (int i = 0; i < numPairs; i++) {
            String k = in.readUTF();
            int length = in.readInt();
            if (length < 0) {
                sendMessage("Comprimento do valor inválido para a chave: " + k);
                return;
            }
            byte[] val = new byte[length];
            in.readFully(val);
            pairs.put(k, val);
        }
        Server.multiPut(pairs);
        sendMessage("Pares chave-valor inseridos/atualizados atomicamente.");
    }

    private void handleMultiGet() throws IOException {
        int numKeys = in.readInt();
        if (numKeys <= 0) {
            sendMessage("Número de chaves inválido.");
            return;
        }
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < numKeys; i++) {
            keys.add(in.readUTF());
        }
        Map<String, byte[]> result = Server.multiGet(keys);
        out.writeInt(result.size());
        for (Map.Entry<String, byte[]> entry : result.entrySet()) {
            out.writeUTF(entry.getKey());
            out.writeInt(entry.getValue().length);
            out.write(entry.getValue());
        }
        out.flush();
    }
}
