package project;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class ClientLibrary {
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    public ClientLibrary(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    /**
     * Autentica o utilizador (ou regista se necessário) e aguarda até o
     * servidor enviar "Sessão disponível!" caso haja fila de espera.
     */
    public boolean authenticate(String username, String password) throws IOException {
        // 1) Ler "Insira o seu nome de utilizador:"
        String serverMessage = receiveMessage();
        System.out.println("Server> " + serverMessage);

        // Enviar username
        sendMessage(username);

        // 2) Ler "Insira a sua palavra-passe:"
        serverMessage = receiveMessage();
        System.out.println("Server> " + serverMessage);

        // Enviar password
        sendMessage(password);

        // Agora, o servidor pode mandar várias mensagens
        // ("Autenticação bem-sucedida.", "Registo bem-sucedido.",
        //  "Aguardando vaga...", "Sessão disponível!", ou "Autenticação falhada.")
        while (true) {
            String response;
            try {
                response = receiveMessage();
            } catch (EOFException e) {
                return false;
            }

            System.out.println("Server> " + response);

            // Se perguntar se deseja registar...
            if (response.contains("Deseja registar-se? (sim/nao)")) {
                Scanner scanner = new Scanner(System.in);
                System.out.print("Resposta (sim/nao): ");
                String userResp = scanner.nextLine();
                sendMessage(userResp);
            }
            // Falha
            else if (response.contains("Autenticação falhada.")
                    || response.contains("Registo falhado.")) {
                return false;
            }
            // Se for "Autenticação bem-sucedida." ou "Registo bem-sucedido.",
            // continuamos a ler pois pode vir a mensagem de aguardo.
            else if (response.contains("Autenticação bem-sucedida.")
                    || response.contains("Registo bem-sucedido.")) {
                // não damos return ainda
            }
            // Se vier "Aguardando vaga... Você está na fila."
            // significa que o servidor está cheio e este cliente está em espera.
            else if (response.contains("Aguardando vaga...")) {
                // apenas continua lendo;
                // quando houver vaga, o servidor enviará "Sessão disponível!"
            }
            // Quando vier "Sessão disponível!"
            else if (response.contains("Sessão disponível!")) {
                // Agora estamos prontos
                return true;
            }
        }
    }

    public void put(String key, byte[] value) throws IOException {
        sendMessage("put");
        sendMessage(key);
        out.writeInt(value.length);
        out.write(value);
        out.flush();
        String response = receiveMessage();
        System.out.println(response);
    }

    public byte[] get(String key) throws IOException {
        sendMessage("get");
        sendMessage(key);
        int length = in.readInt();
        if (length > 0) {
            byte[] value = new byte[length];
            in.readFully(value);
            return value;
        } else {
            System.out.println("Chave não encontrada.");
            return null;
        }
    }

    public void multiPut(Map<String, byte[]> pairs) throws IOException {
        sendMessage("multiPut");
        out.writeInt(pairs.size());
        for (Map.Entry<String, byte[]> entry : pairs.entrySet()) {
            sendMessage(entry.getKey());
            out.writeInt(entry.getValue().length);
            out.write(entry.getValue());
        }
        out.flush();
        String response = receiveMessage();
        System.out.println(response);
    }

    public Map<String, byte[]> multiGet(Set<String> keys) throws IOException {
        sendMessage("multiGet");
        out.writeInt(keys.size());
        for (String key : keys) {
            sendMessage(key);
        }
        out.flush();
        int numPairs = in.readInt();
        Map<String, byte[]> result = new HashMap<>();
        for (int i = 0; i < numPairs; i++) {
            String k = in.readUTF();
            int length = in.readInt();
            byte[] val = new byte[length];
            in.readFully(val);
            result.put(k, val);
        }
        return result;
    }

    public void logout() throws IOException {
        sendMessage("logout");
    }

    public void close() throws IOException {
        socket.close();
    }

    // ---------------------------------------------------
    // Métodos auxiliares
    // ---------------------------------------------------
    private void sendMessage(String message) throws IOException {
        out.writeUTF(message);
        out.flush();
    }

    private String receiveMessage() throws IOException {
        return in.readUTF();
    }
}
