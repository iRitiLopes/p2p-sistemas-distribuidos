package com.jp2p.p2p;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Client extends Thread {
    public static class Menu {
        void show() {
            System.out.println("1 - SEARCH <nome do arquivo que procura>");
            System.out.println("2 - DOWNLOAD <nome do arquivo que procura> <endereco do peer que possui>");
            System.out.println("3 - SAIR");
        }

        public Message captureMessage() {
            String[] splitMessage;
            splitMessage = read();
            while (!isValid(splitMessage)) {
                System.out.println("Escolha inválida");
                show();
                splitMessage = read();
            }

            switch (splitMessage[0]) {
                case "1":
                    return parseSearch(splitMessage);
                case "2":
                    return parseDownload(splitMessage);
                case "3":
                    return parseLeave(splitMessage);
            }
            return new Message();
        }

        private boolean isValid(String[] splitMessage) {
            switch (splitMessage[0]) {
                case "1":
                case "2":
                case "3":
                    return true;
                default:
                    return false;
            }
        }

        private String[] read() {
            Scanner reader = new Scanner(System.in);
            String message = reader.nextLine();
            return message.split(" ");
        }

        private Message parseLeave(String[] splitMessage) {
            Message message = new Message();
            message.setLeave();
            return message;
        }

        private Message parseDownload(String[] splitMessage) {
            Message message = new Message();
            message.setAskDownload();
            message.fileToDownload = splitMessage[1];
            message.peerToRequestDownload = splitMessage[2];
            return message;
        }

        private Message parseSearch(String[] splitMessage) {
            Message message = new Message();
            message.setSearch();
            message.fileToSearch = splitMessage[1];
            return message;
        }
    }

    private class AliveHandler extends Thread {
        private byte[] buf = new byte[4098];
        private DatagramPacket packet;
        private InetAddress serverKeepAliveAddress;
        private int serverKeepAlivePort;

        @Override public void run() {
            while(true){
                try {
                    if(readMessageAlive().isAlive()){
                        handleAliveRequest();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        private void handleAliveRequest() throws IOException {
            Message message = new Message();
            message.setAliveOK();
            this.sendMessageAlive(message);
        }

        public void sendMessageAlive(Message message) throws IOException {
            this.buf = message.serialize().getBytes();
            packet = new DatagramPacket(this.buf, this.buf.length, serverKeepAliveAddress, serverKeepAlivePort);
            heartbeatSocket.send(packet);
        }

        public Message readMessageAlive() throws IOException {
            packet = new DatagramPacket(this.buf, this.buf.length);
            heartbeatSocket.receive(packet);
            serverKeepAliveAddress = packet.getAddress();
            serverKeepAlivePort = packet.getPort();
            String response = new String(this.packet.getData(), 0, packet.getLength());
            return Message.fromString(response);
        }
    }

    private class DownloadHandler extends Thread {
        ServerSocket serverSocket;
        DatagramSocket downloadUDPSocket;
        InetAddress addressUDPToResponse;
        int portUPDToResponse;
        boolean isDownloading = false;
        byte[] buf = new byte[4098];

        DownloadHandler(DatagramSocket udpSocket, ServerSocket tcpSocket){
            this.downloadUDPSocket = udpSocket;
            this.serverSocket = tcpSocket;
        }

        @Override public void run() {
            while (true){
                try {
                    Message message = readUDPMessage();
                    if(message.isDownloadRequest()){
                        handleDownloadRequest(message);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void handleDownloadRequest(Message message) throws IOException {
            File file = null;
            Message response = new Message();
            response.setDownloadNOK();
            if(isDownloading){
                sendMessage(response);
                return;
            }
            for(File f: files){
                if(f.getName().equals(message.fileToDownload)){
                    response.setDownloadOK();
                    file = f;
                }
            }
            sendMessage(response, addressUDPToResponse, portUPDToResponse);
            if(file == null){
                return;
            }

            FileInputStream fis = new FileInputStream(file);
            Socket socket = serverSocket.accept();
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            byte[] filebuf = new byte[4098];

            int length = 0;
            long progress = 0;
            while((length = fis.read(filebuf, 0, filebuf.length)) != -1){
                out.write(filebuf, 0, length);
                out.flush();
                progress += length;
            }
            socket.close();
            fis.close();
        }

        private Message readTCPMessage() throws IOException {
            Socket socket = serverSocket.accept();
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            buf = new byte[4098];
            in.read(buf, 0, buf.length);
            Message message = Message.fromString(new String(buf, 0, buf.length));
            System.out.println("Do tcp: " + message.serialize());
            return message;
        }

        public Message readUDPMessage() throws IOException {
            this.buf = new byte[4098];
            DatagramPacket packet = new DatagramPacket(this.buf, this.buf.length);
            downloadUDPSocket.receive(packet);
            String response = new String(packet.getData(), 0, packet.getLength());
            this.addressUDPToResponse = packet.getAddress();
            this.portUPDToResponse = packet.getPort();
            return Message.fromString(response);
        }
    }


    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private String directory;
    private List<File> files;
    private Menu menu;
    private DatagramSocket udpSocket;
    private DatagramSocket heartbeatSocket;
    private final DatagramSocket downloadUDPSocket;
    private final ServerSocket downloadTCPSocket;
    private DatagramPacket packet;
    private InetAddress addr = InetAddress.getByName("localhost");
    private int port = 10000;
    private byte[] buf;


    public Client(String directory) throws Exception {
        this.udpSocket = new DatagramSocket();
        this.heartbeatSocket = new DatagramSocket(udpSocket.getLocalPort() + 1);
        this.downloadUDPSocket = new DatagramSocket(udpSocket.getLocalPort() + 2);
        this.downloadTCPSocket = new ServerSocket(udpSocket.getLocalPort() + 3);
        this.directory = directory;
        this.files = listFilesOnDir(directory);
        this.menu = new Menu();
    }

    public List<File> listFilesOnDir(String dir) throws Exception {
        try (Stream<Path> stream = Files.list(Paths.get(dir))) {
            return stream.filter(file -> !Files.isDirectory(file))
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            throw new Exception("Falha ao listar o diretório");
        }
    }

    public void sendMessage(Message message) throws IOException {
        this.buf = message.serialize().getBytes();
        packet = new DatagramPacket(this.buf, this.buf.length, addr, port);
        this.udpSocket.send(packet);
    }
    public void sendMessage(Message message, InetAddress address, int port) throws IOException {
        this.buf = message.serialize().getBytes();
        packet = new DatagramPacket(this.buf, this.buf.length, address, port);
        this.udpSocket.send(packet);
    }

    public Message readMessage() throws IOException {
        //return new Gson().fromJson(in.readLine(), Message.class);
        this.buf = new byte[4098];
        packet = new DatagramPacket(this.buf, this.buf.length);
        udpSocket.receive(packet);
        String response = new String(this.packet.getData(), 0, packet.getLength());
        return Message.fromString(response);
    }

    @Override public void run() {
        try {
            //connect();

            Thread aliveHandler = new AliveHandler();
            DownloadHandler downloadHandler = new DownloadHandler(downloadUDPSocket, downloadTCPSocket);

            Message message = new Message();
            message.setJoin();
            for (File f : this.files) {
                message.filenames.add(f.getName());
            }
            sendMessage(message);

            Message parsedResponse = readMessage();
            if (!parsedResponse.isJoinOK()) {
                return;
            }
            aliveHandler.start();
            downloadHandler.start();
            menu.show();
            message = menu.captureMessage();
            while (!parsedResponse.isLeave()) {
                if (message.isSearchRequest()) {
                    handleSearchRequest(message);
                }else if(message.isAskDownload()){
                    handleAskDownloadRequest(message);
                }
                menu.show();
                message = menu.captureMessage();
            }
            handleLeaveRequest(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleAskDownloadRequest(Message message) throws IOException {
        String filename = message.fileToDownload;
        String host = message.peerToRequestDownload.split(":")[0];
        int port = Integer.parseInt(message.peerToRequestDownload.split(":")[1]);
        message.setDownload();
        sendMessage(message, InetAddress.getByName(host), port + 2);
        Message response = readMessage();
        if(response.isDownloadNOK()){
            System.out.println("Download negado!");
            return;
        }
        if(response.isDownloadOK()){
            Socket socket = new Socket(host, port + 3);
            message = new Message();
            message.setDownload();
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            File file = new File(directory +  "/" + filename);
            FileOutputStream fos = new FileOutputStream(file);
            byte[] filebuf = new byte[4098];
            int count;
            while((count = in.read(filebuf)) != -1){
                fos.write(filebuf, 0, count);
            }
            System.out.println("Download Completo!");
            fos.close();
            socket.close();
            Message messageToUpdate = new Message();
            messageToUpdate.setUpdate();
            messageToUpdate.fileToUpdate = filename;
            sendMessage(messageToUpdate);
            files.add(file);
            System.out.println("arquivos atualizados");
            System.out.println(files);
            return;
        }
    }

    private void handleLeaveRequest(Message message) throws IOException {
        sendMessage(message);
        closeSocket();
    }

    private void handleSearchRequest(Message message) throws IOException {
        sendMessage(message);
        Message response = readMessage();
        if (response.isSearchNOK()) {
            System.out.println("Arquivo não encontrado");
        } else if (response.isSearchOK()) {
            System.out.println("Arquivo encontrado!\nPeers com o arquivo:");
            System.out.println(response.peerAddressWithFile + ":" + response.peerPortWithFile);
        }
    }

    private void closeSocket() throws IOException {
        in.close();
        out.close();
        clientSocket.close();
    }


    public static void main(String[] args) throws Exception {
        if(args.length < 1){
            System.out.println("Necessita argumento com o diretorio dos arquivos");
            return;
        }
        String path = args[0];
        Client client = new Client(path);
        client.start();
    }
}
