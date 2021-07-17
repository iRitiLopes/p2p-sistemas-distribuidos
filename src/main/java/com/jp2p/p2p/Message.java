package com.jp2p.p2p;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.*;


//Classe responsavel para troca de mensagens. Transforma o JSON em objeto e vice-versa. Possui propriedades que auxiliam na comunicação
public class Message {

    public static Message fromPacket(DatagramPacket packet) {
        return Message.fromString(new String(packet.getData()));
    }

    public void setJoin() {
        this.messageType = MessageType.JOIN;
    }

    public boolean isLeave() {
        return this.messageType == MessageType.LEAVE;
    }

    public void setLeave() {
        messageType = MessageType.LEAVE;
    }

    public void setSearchOK() {
        messageType = MessageType.SEARCH_OK;
    }

    public void setSearch() {
        messageType = MessageType.SEARCH;
    }

    public void setSearchNOK() {
        messageType = MessageType.SEARCH_NOK;
    }

    public void setLeaveOK() {
        messageType = MessageType.LEAVE_OK;
    }

    public void setAlive() {
        messageType = MessageType.ALIVE;
    }

    public boolean isAlive() {
        return messageType == MessageType.ALIVE;
    }

    public void setAliveOK() {
        messageType = MessageType.ALIVE_OK;
    }

    public boolean isJoinOK() {
        return messageType == MessageType.JOIN_OK;
    }

    public void setAskDownload() {
        this.messageType = MessageType.ASK_DOWNLOAD;
    }

    public boolean isSearchNOK() {
        return this.messageType == MessageType.SEARCH_NOK;
    }

    public boolean isSearchOK() {
        return this.messageType == MessageType.SEARCH_OK;
    }

    public boolean isDownloadRequest() {
        return this.messageType == MessageType.DOWNLOAD;
    }

    public boolean isAskDownload() {
        return this.messageType == MessageType.ASK_DOWNLOAD;
    }

    public void setDownload() {
        this.messageType = MessageType.DOWNLOAD;
    }

    public void setDownloadOK() {
        this.messageType = MessageType.DOWNLOAD_OK;
    }

    public void setDownloadNOK() {
        this.messageType = MessageType.DOWNLOAD_NOK;
    }

    public boolean isDownloadNOK() {
        return this.messageType == MessageType.DOWNLOAD_NOK;
    }

    public boolean isDownloadOK() {
        return this.messageType == MessageType.DOWNLOAD_OK;
    }

    public void setUpdate() {
        messageType = MessageType.UPDATE;
    }

    public boolean isUpdate() {
        return messageType == MessageType.UPDATE;
    }


    public enum MessageType {
        JOIN, JOIN_OK, LEAVE, LEAVE_OK, ALIVE, ALIVE_OK, UPDATE, UPDATE_OK, SEARCH, SEARCH_OK, SEARCH_NOK, ASK_DOWNLOAD, DOWNLOAD, DOWNLOAD_OK, DOWNLOAD_NOK
    }


    MessageType messageType;
    ArrayList<String> filenames;
    String fileToSearch;
    public String peerToRequestDownload;
    public List<String> clientsWithFile;
    public List<Integer> peerPortWithFile;
    public String fileToDownload;
    public String fileToUpdate;

    public Message() {
        filenames = new ArrayList<>();
        fileToSearch = "";
        clientsWithFile = new ArrayList<>();
        peerPortWithFile = new ArrayList<>();
    }

    public void setJoinOK() {
        this.messageType = MessageType.JOIN_OK;
    }

    public boolean isJoinRequest() {
        return this.messageType == MessageType.JOIN;
    }

    public boolean isAliveOK() {
        return messageType == MessageType.ALIVE_OK;
    }

    public boolean isSearchRequest() {
        return this.messageType == MessageType.SEARCH;
    }

    public boolean isUpdateRequest() {
        return this.messageType == MessageType.UPDATE;
    }

    public void send(PrintWriter out, String message) throws Exception {
        try {
            out.println(message);
            out.flush();
        } catch (Exception e) {
            throw new Exception(e);
        }
    }

    public String serialize() {
        return new Gson().toJson(this);
    }

    public Message deserialize(String message) {
        return new Gson().fromJson(message, Message.class);
    }

    public static Message fromString(String msg) {
        return new Gson().fromJson(msg.trim(), Message.class);
    }
}
