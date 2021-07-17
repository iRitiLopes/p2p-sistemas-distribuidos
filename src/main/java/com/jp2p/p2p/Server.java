package com.jp2p.p2p;

import java.net.*;
import java.io.*;
import java.time.Duration;
import java.util.*;

public class Server {
    private class ClientDTO {
        InetAddress address;
        int port;
        int keepAlivePort;

        public ClientDTO(InetAddress address, int port) {
            this.address = address;
            this.port = port;
            this.keepAlivePort = port + 1;
        }

        @Override
        public String toString() {
            return "ClientDTO{" + "address=" + address + ", port=" + port + ", keepAlivePort=" + keepAlivePort + '}';
        }
    }

    private class KeepAlive extends Thread {
        @Override
        public void run() {
            while (true) {
                List<ClientHandler> toRemoveClients = new ArrayList<>();
                for (ClientDTO client : clients) {
                    ClientHandler clientHandler = new ClientHandler(client.address, client.port);
                    toRemoveClients.add(clientHandler);
                    clientHandler.checkKeepAlive();
                }
                try {
                    Thread.sleep(Duration.ofSeconds(30).toMillis());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                for (ClientHandler client : toRemoveClients) {
                    if (!client.isAlive) {
                        ClientDTO clientToRemove = findClient(client.address, client.port);
                        assert clientToRemove != null;
                        System.out.println("Peer - " + clientToRemove.address + ":" + clientToRemove.port + " morto!");
                        clients.remove(clientToRemove);
                        removeClientsFiles(clientToRemove);

                    } else {
                        System.out.println("Client - " + client.address + ":" + client.port + "is Alive");
                    }
                }
            }
        }
    }

    private void removeClientsFiles(ClientDTO clientToRemove) {
        List<String> clientFiles = new ArrayList<>();
        filesController.forEach((filename, clients) -> {
            if (clients.contains(clientToRemove)) {
                clientFiles.add(filename);
            }
        });
        for (String filename : clientFiles) {
            List<ClientDTO> clients = filesController.get(filename);
            clients.remove(clientToRemove);
            filesController.put(filename, clients);
        }
    }

    private class ClientHandler extends Thread {
        private final int keepAlivePort;
        final InetAddress address;
        final int port;
        boolean isAlive = true;
        Message request;

        ClientHandler(InetAddress address, int port) {
            this.address = address;
            this.port = port;
            this.keepAlivePort = port + 1;
        }

        public void setRequest(Message message) {
            this.request = message;
        }

        @Override
        public void run() {
            try {
                if (this.request.isJoinRequest()) {
                    handleJoinRequest();
                } else if (this.request.isSearchRequest()) {
                    handleSearchRequest();
                } else if (this.request.isAlive()) {
                    handleIsAliveRequest();
                } else if (this.request.isUpdate()) {
                    handleUpdateRequest();
                } else if (this.request.isLeave()) {
                    handleLeaveRequest();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        /*
        Método responsável de lidar quando o cliente faz uma requisição de LEAVE
         */
        private void handleLeaveRequest() throws IOException {
            System.out.println("Peer - " + address + ":" + port + "IS LEAVING!");
            ClientDTO clientDTO = findClient(address, port);
            Message response = new Message();
            response.setLeaveOK();
            removeClientsFiles(clientDTO);
            clients.remove(clientDTO);
            sendMessage(response);
        }

        /*
        Método responsável de lidar com o UPDATE de arquivo de um cliente, logo após um DOWNLOAD ter sido concluido
         */
        private void handleUpdateRequest() {
            ClientDTO client = findClient(address, port);
            List<ClientDTO> clientsWithFile = filesController.getOrDefault(request.fileToUpdate, new ArrayList<>());
            clientsWithFile.add(client);
        }

        /*
        Mẽtodo responsável de lidar com o pedido de SEARCH do cliente, buscando qual cliente possui o arquivo pedido
         */
        private void handleSearchRequest() throws IOException {
            System.out.println("Peer: " + address.toString() + ":" + port + " Searching for - File: " + request.fileToSearch);
            List<ClientDTO> clientsWithFile = filesController.getOrDefault(request.fileToSearch, new ArrayList<>());
            Message message = new Message();

            List<ClientDTO> clientsWithFileFiltered = new ArrayList<>();

            clientsWithFile.forEach(client -> {
                if (client.port != port) {
                    clientsWithFileFiltered.add(client);
                } else if (!client.address.toString().equals(address.toString())) {
                    clientsWithFileFiltered.add(client);
                }
            });

            if (clientsWithFileFiltered.isEmpty()) {
                message.setSearchNOK();
                sendMessage(message);
                return;
            }

            message.setSearchOK();
            message.peerAddressWithFile = clientsWithFileFiltered.get(0).address.toString();
            message.peerPortWithFile = clientsWithFileFiltered.get(0).port;
            System.out.println(message.serialize());
            sendMessage(message);

        }

        /*
        Metodo responsãvel de lidar com ALIVE dos clientes, para saber se ainda estão vivos
         */
        private void handleIsAliveRequest() throws IOException {
            sendCheckAlive(this.request);
        }

        /*
        Metodo responsável de lidar com o pedido de JOIN do cliente, adiciona os arquivos do cliente ao controle do servidor
         */
        private void handleJoinRequest() throws IOException {
            System.out.println("Peer: " + address.toString() + ":" + port + " ASK TO JOIN - Files: " + request.filenames.toString());
            addFilesToController();
            Message response = new Message();
            response.setJoinOK();
            sendMessage(response);
        }

        private void sendMessage(Message response) throws IOException {
            byte[] buf;
            buf = response.serialize().getBytes();
            DatagramPacket pkt = new DatagramPacket(buf, buf.length, address, port);
            socket.send(pkt);
        }


        /*
        Metodo onde acontece a checagem do ALIVE, envio de mensagens e tratativas
         */
        private void sendCheckAlive(Message message) throws IOException {
            byte[] buf;
            buf = message.serialize().getBytes();
            DatagramSocket keepAliveSocket = new DatagramSocket();
            keepAliveSocket.setSoTimeout(30000);
            DatagramPacket pkt = new DatagramPacket(buf, buf.length, address, keepAlivePort);
            keepAliveSocket.send(pkt);
            try {
                buf = new byte[4098];
                pkt = new DatagramPacket(buf, buf.length, address, keepAlivePort);
                keepAliveSocket.receive(pkt);
                String response = new String(pkt.getData());
                Message respMessage = Message.fromString(response);
                if (respMessage.isAliveOK()) {
                    isAlive = true;
                }
            } catch (SocketTimeoutException e) {
                isAlive = false;
            }
        }

        private void addFilesToController() {
            for (String filename : this.request.filenames) {
                if (!filesController.containsKey(filename)) {
                    filesController.put(filename, new ArrayList<>());
                }
                List<ClientDTO> clientsWithFile = filesController.get(filename);

                boolean found = false;
                for (ClientDTO client : clientsWithFile) {
                    if (client.address.equals(address) && client.port == port) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    clientsWithFile.add(new ClientDTO(address, port));
                }
                filesController.put(filename, clientsWithFile);
            }
        }

        public void handle(Message message) {
            System.out.println(message.serialize());
            setRequest(message);
            start();
        }

        public void checkKeepAlive() {
            isAlive = false;
            System.out.println("Checking peer - " + address + ":" + port + " if is alive!");
            Message message = new Message();
            message.setAlive();
            handle(message);
        }
    }


    DatagramSocket socket;
    DatagramPacket packet;
    int serverPort = 10098;
    byte[] buffer = new byte[4098];
    List<ClientDTO> clients;
    Map<String, List<ClientDTO>> filesController;

    Server(int port) {
        this.serverPort = port > 0 ? port : 10098;
        clients = new ArrayList<ClientDTO>();
        filesController = new HashMap<>();
    }

    public void listen() throws IOException {
        socket = new DatagramSocket(10098);
        KeepAlive keepAlive = new KeepAlive();
        keepAlive.start();
        while (true) {
            System.out.println("LISTENING - AWAITING REQUESTS");
            handleRequest();
        }

    }

    /*
    Método que lida com as requisições dos clientes
     */
    private void handleRequest() throws IOException {
        buffer = new byte[4098];
        packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        InetAddress address = packet.getAddress();
        int port = packet.getPort();
        packet = new DatagramPacket(buffer, buffer.length, address, port);
        Message message = Message.fromPacket(packet);
        String clientId = clientId(address, port);

        ClientDTO clientInfo = findClient(address, port);
        if (clientInfo == null) {
            addClient(address, port);
        }
        ClientHandler client = new ClientHandler(address, port);
        client.handle(message);
    }

    private ClientDTO findClient(InetAddress address, int port) {
        for (ClientDTO client : clients) {
            if (client.address.toString().equals(address.toString()) && client.port == port) {
                return client;
            }
        }
        return null;
    }

    private void addClient(InetAddress address, int port) {
        clients.add(new ClientDTO(address, port));
    }

    private String clientId(InetAddress address, int port) {
        return address.toString() + ":" + port;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Qual a porta do servidor: [default -> 10098]");
        Scanner scanner = new Scanner(System.in);
        String input = scanner.nextLine();
        int port = input.isEmpty() ? 10098 : Integer.parseInt(input);
        if (args.length == 1) {
            port = Integer.parseInt(args[0]);
        }
        (new Server(port)).listen();
    }
}
