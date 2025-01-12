package project;

import java.io.IOException;
import java.util.*;

public class ClientInterface {
    public static void main(String[] args) {
        try {
            ClientLibrary client = new ClientLibrary("localhost", 12345);
            Scanner scanner = new Scanner(System.in);

            // Autenticação
            boolean authenticated = false;
            while (!authenticated) {
                System.out.print("Nome de utilizador: ");
                String username = scanner.nextLine();
                System.out.print("Palavra-passe: ");
                String password = scanner.nextLine();

                authenticated = client.authenticate(username, password);
                if (!authenticated) {
                    System.out.println("Falha na autenticação ou registo. Tente novamente.\n");
                }
            }

            System.out.println("Autenticado e sessão obtida com sucesso!");

            // Loop principal
            boolean running = true;
            while (running) {
                System.out.println("\nEscolha uma opção:");
                System.out.println("1. put");
                System.out.println("2. get");
                System.out.println("3. multiPut");
                System.out.println("4. multiGet");
                System.out.println("5. Sair");
                System.out.print("Opção: ");
                String optionStr = scanner.nextLine();

                int option;
                try {
                    option = Integer.parseInt(optionStr);
                } catch (NumberFormatException e) {
                    System.out.println("Opção inválida.");
                    continue;
                }

                switch (option) {
                    case 1:
                        System.out.print("Chave: ");
                        String keyPut = scanner.nextLine();
                        System.out.print("Valor: ");
                        String valuePut = scanner.nextLine();
                        client.put(keyPut, valuePut.getBytes());
                        break;
                    case 2:
                        System.out.print("Chave: ");
                        String keyGet = scanner.nextLine();
                        byte[] valueGet = client.get(keyGet);
                        if (valueGet != null) {
                            System.out.println("Valor obtido: " + new String(valueGet));
                        }
                        break;
                    case 3:
                        Map<String, byte[]> pairs = new HashMap<>();
                        System.out.print("Número de pares a inserir: ");
                        String numPairsStr = scanner.nextLine();
                        int numPairs;
                        try {
                            numPairs = Integer.parseInt(numPairsStr);
                        } catch (NumberFormatException e) {
                            System.out.println("Número inválido.");
                            break;
                        }
                        for (int i = 0; i < numPairs; i++) {
                            System.out.print("Chave " + (i + 1) + ": ");
                            String k = scanner.nextLine();
                            System.out.print("Valor " + (i + 1) + ": ");
                            String v = scanner.nextLine();
                            pairs.put(k, v.getBytes());
                        }
                        client.multiPut(pairs);
                        break;
                    case 4:
                        Set<String> keys = new HashSet<>();
                        System.out.print("Número de chaves a obter: ");
                        String numKeysStr = scanner.nextLine();
                        int numKeys;
                        try {
                            numKeys = Integer.parseInt(numKeysStr);
                        } catch (NumberFormatException e) {
                            System.out.println("Número inválido.");
                            break;
                        }
                        for (int i = 0; i < numKeys; i++) {
                            System.out.print("Chave " + (i + 1) + ": ");
                            String k = scanner.nextLine();
                            keys.add(k);
                        }
                        Map<String, byte[]> results = client.multiGet(keys);
                        System.out.println("Resultados obtidos:");
                        for (Map.Entry<String, byte[]> entry : results.entrySet()) {
                            System.out.println(entry.getKey() + ": " + new String(entry.getValue()));
                        }
                        break;
                    case 5:
                        running = false;
                        client.logout();
                        client.close();
                        System.out.println("Sessão encerrada. Até breve!");
                        break;
                    default:
                        System.out.println("Opção inválida. Por favor, escolha entre 1 e 5.");
                }
            }

            scanner.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
