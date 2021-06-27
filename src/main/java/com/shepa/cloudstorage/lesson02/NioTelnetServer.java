package com.shepa.cloudstorage.lesson02;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class NioTelnetServer {
    private static final String LS_COMMAND = "\tls          view all files from current directory" + System.lineSeparator();
    private static final String MKDIR_COMMAND = "\tmkdir       view all files from current directory" + System.lineSeparator();
    private static final String TOUCH_COMMAND = "\ttouch       create new file" + System.lineSeparator();
    private static final String CD_COMMAND = "\tcd          change current directory" + System.lineSeparator();
    private static final String RM_COMMAND = "\trm          delete file or directory" + System.lineSeparator();
    private static final String COPY_COMMAND = "\tcopy        copy file or directory" + System.lineSeparator();
    private static final String CAT_COMMAND = "\tcat         print text file" + System.lineSeparator();
    private static final String CHANGENICK_COMMAND = "\tchangenick  change nickname" + System.lineSeparator();
    private final String ROOT_DIR = "server";

    private final ByteBuffer buffer = ByteBuffer.allocate(512);

    private Map<SocketAddress, String> clients = new HashMap<>();
    private Map<SocketAddress, Path> currentPaths = new HashMap<>();

    public NioTelnetServer() throws Exception {
        ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress(5679));
        server.configureBlocking(false);
        Selector selector = Selector.open();

        server.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("Server started");
        while (server.isOpen()) {
            selector.select();
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                if (key.isAcceptable()) {
                    handleAccept(key, selector);
                } else if (key.isReadable()) {
                    handleRead(key, selector);
                }
                iterator.remove();
            }
        }
    }

    private void handleAccept(SelectionKey key, Selector selector) throws IOException {
        SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
        InetSocketAddress remoteAddress = (InetSocketAddress) channel.getRemoteAddress();
        String hostName = remoteAddress.getHostName();
        clients.put(remoteAddress, hostName);
        channel.configureBlocking(false);
        System.out.println("Client connected. IP: " + remoteAddress.getAddress());
        channel.register(selector, SelectionKey.OP_READ, "skjghksdhg");
        String helloMessage = "Hello user " + hostName + "!" + System.lineSeparator();
        channel.write(ByteBuffer.wrap(helloMessage.getBytes(StandardCharsets.UTF_8)));
        channel.write(ByteBuffer.wrap("Enter --help for support info".getBytes(StandardCharsets.UTF_8)));
        Path userDir = Paths.get(ROOT_DIR + File.separator, hostName);
        if (!Files.exists(userDir)) {
            Files.createDirectory(userDir);
        }
        currentPaths.put(remoteAddress, userDir);
        printInviteString(channel);
    }

    private void printInviteString(SocketChannel channel) throws IOException {
        InetSocketAddress remoteAddress = (InetSocketAddress) channel.getRemoteAddress();
        String nickname = clients.get(remoteAddress);
        String inviteString = System.lineSeparator() + nickname + " " + pathStringForClient(remoteAddress) + ">";
        channel.write(ByteBuffer.wrap(inviteString.getBytes(StandardCharsets.UTF_8)));
    }

    private String pathStringForClient(InetSocketAddress remoteAddress) {
        StringBuilder sb = new StringBuilder();
        sb.append("~\\");
        Path currentPath = currentPaths.get(remoteAddress);
        Iterator<Path> iterator = currentPath.iterator();
        while(iterator.hasNext()){
            Path path = iterator.next();
            if ("server".equals(path.toString())) {
                continue;
            } else if (remoteAddress.getHostName().equals(path.toString())) {
                continue;
            } else {
                sb.append(path.toString() + "\\");
            }
        }
        return sb.toString();
    }


    private void handleRead(SelectionKey key, Selector selector) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        SocketAddress client = channel.getRemoteAddress();
        int readBytes = channel.read(buffer);

        if (readBytes < 0) {
            channel.close();
            return;
        } else  if (readBytes == 0) {
            return;
        }

        buffer.flip();
        StringBuilder sb = new StringBuilder();
        while (buffer.hasRemaining()) {
            sb.append((char) buffer.get());
        }
        buffer.clear();

        if (key.isValid()) {
            String[] command = sb.toString()
                    .replace("\n", "")
                    .replace("\r", "").split(" ");

            if ("--help".equals(command[0])) {
                sendMessage(LS_COMMAND, selector, client);
                sendMessage(MKDIR_COMMAND, selector, client);
                sendMessage(TOUCH_COMMAND, selector, client);
                sendMessage(CD_COMMAND, selector, client);
                sendMessage(RM_COMMAND, selector, client);
                sendMessage(COPY_COMMAND, selector, client);
                sendMessage(CAT_COMMAND, selector, client);
                sendMessage(CHANGENICK_COMMAND, selector, client);
                printInviteString(channel);
            } else if ("ls".equals(command[0])) {
                sendMessage(getFilesList(client).concat(System.lineSeparator()), selector, client);
                printInviteString(channel);
            } else if ("changenick".equals(command[0])) {
                if (command.length < 2) {
                    return;
                }
                clients.put(channel.getRemoteAddress(), command[1]);
                printInviteString(channel);
            } else if ("cd".equals(command[0])) {
                if (command.length < 2) {
                    return;
                }
                if (!changeCurrentDir(client, command[1])) {
                    sendMessage("Directory doesn't exist", selector, client);
                }
                printInviteString(channel);
            } else if ("touch".equals(command[0])) {
                if (command.length < 2) {
                    return;
                }
                if (!createFile(client, command[1], true)) {
                    sendMessage("File is already exist", selector, client);
                }
                printInviteString(channel);
            } else if ("mkdir".equals(command[0])) {
                if (command.length < 2) {
                    return;
                }
                if (!createFile(client, command[1], false)) {
                    sendMessage("Directory is already exist", selector, client);
                }
                printInviteString(channel);
            } else if ("rm".equals(command[0])) {
                if (command.length < 2) {
                    return;
                }
                deleteFileOrDirectory(client, command[1], selector);
                printInviteString(channel);
            } else if ("copy".equals(command[0])) {
                if (command.length < 3) {
                    return;
                }
                copyFileOrDirectory(client, command[1], command[2], selector);
                printInviteString(channel);
            } else if ("cat".equals(command[0])) {
                if (command.length < 2) {
                    return;
                }
                printTextFile(client, command[1], selector);
                printInviteString(channel);
            }
        }
    }

    private void printTextFile(SocketAddress client, String filename, Selector selector) throws IOException {
        Path path = getPathToFile(client, filename);
        if (Files.isDirectory(path)) {
            sendMessage("Can't read directory", selector, client);
            return;
        }
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        for (String line : lines) {
            sendMessage(line, selector, client);
            sendMessage(System.lineSeparator(), selector, client);
        }

    }

    private boolean createFile(SocketAddress client, String filename, boolean isFile) throws IOException {
        Path path = getPathToFile(client, filename);
        if (!Files.exists(path)) {
            if (isFile) {
                Files.createFile(path);
            } else {
                Files.createDirectories(path);
            }
            return true;
        }
        return false;
    }

    private void deleteFileOrDirectory(SocketAddress client, String filename, Selector selector) throws IOException {
        Path path = getPathToFile(client, filename);
        if (!Files.exists(path)) {
            sendMessage("File or directory doesn't exist", selector, client);
            return;
        }
        if (Files.isDirectory(path)) {
            String[] siblings = new File(path.toString()).list();
            if (siblings.length > 0) {
                sendMessage("Directory is not empty", selector, client);
                return;
            }
        }
        Files.delete(path);
    }

    private void copyFileOrDirectory(SocketAddress client, String source, String target, Selector selector) throws IOException {
        Path pathToSource = getPathToFile(client, source);
        if (!Files.exists(pathToSource)) {
            sendMessage("Source file doesn't exist", selector, client);
            return;
        }
        Path pathToTarget = getPathToFile(client, target);

        if (Files.isDirectory(pathToSource)) {
            if (!Files.exists(pathToTarget)) {
                Files.createDirectories(pathToTarget);
            }
            Files.walkFileTree(pathToSource, new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Files.copy(dir, pathToTarget.resolve(dir.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.copy(file, pathToTarget.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            Files.copy(pathToSource, pathToTarget, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean changeCurrentDir(SocketAddress client, String newDir) {
        StringBuilder sb = getDefaultPathForClient(client);
        if ("~".equals(newDir)) {
            currentPaths.put(client, Path.of(sb.toString()));
            return true;
        } else if ("..".equals(newDir)) {
            Path currentPath = currentPaths.get(client);
            if (!currentPath.equals(Path.of(sb.toString()))) {
                currentPaths.put(client, currentPath.getParent());
            }
            return true;
        } else {
            String[] dirs = newDir.split("\\\\");
            for (String dir : dirs) {
                sb.append(dir + "\\\\");
            }
        }
        Path newPath = Path.of(sb.toString());
        if (Files.exists(newPath)) {
            currentPaths.put(client, newPath);
            return true;
        } else {
            return false;
        }
    }

    private void sendMessage(String message, Selector selector, SocketAddress client) throws IOException {
        for (SelectionKey key : selector.keys()) {
            if (key.isValid() && key.channel() instanceof SocketChannel) {
                if (((SocketChannel) key.channel()).getRemoteAddress().equals(client)) {
                    ((SocketChannel) key.channel()).write(ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8)));
                }
            }
        }
    }

    private String getFilesList(SocketAddress client) {
        Path currentDir = currentPaths.get(client);
        String[] servers = new File(currentDir.toString()).list();
        return String.join(" ", servers);
    }

    private StringBuilder getDefaultPathForClient(SocketAddress client) {
        StringBuilder sb = new StringBuilder();
        sb.append("server\\" + ((InetSocketAddress)client).getHostName() + "\\\\");
        return sb;
    }

    private Path getPathToFile(SocketAddress client, String fileName) {
        Path path;
        String[] pathList = fileName.split("\\\\");
        if (pathList.length == 1) {
            path = Path.of(currentPaths.get(client).toString(), fileName);
        } else {
            StringBuilder sb = getDefaultPathForClient(client);
            for (String dir : pathList) {
                sb.append(dir + "\\\\");
            }
            path = Path.of(sb.toString());
        }
        return path;
    }

    public static void main(String[] args) throws Exception {
        new NioTelnetServer();
    }
}
