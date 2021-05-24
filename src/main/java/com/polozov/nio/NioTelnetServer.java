package com.polozov.nio;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class NioTelnetServer {
	public static final String LS_COMMAND = "\tls    view all files and directories\n";
	public static final String MKDIR_COMMAND = "\tmkdir    create directory\n";
	public static final String CHANGE_NICKNAME = "\tnick    change nickname\n";
    public static final String TOUCH_COMMAND = "\ttouch     file creation\n";
    public static final String CD_COMMAND = "\tcd   moving a file\n";
    public static final String RM_COMMAND = "\trm    moving a file\n";
    public static final String COPY_COMMAND = "\tcopy   moving a file\n";
    public static final String CAT_COMMAND = "\tcat      read the contents\n";
    private static final String ROOT = "server";

    private Path pathMain = Path.of("server");
    private Map<SocketAddress, String> users = new HashMap<>();

	private final ByteBuffer buffer = ByteBuffer.allocate(512);

	public NioTelnetServer() throws IOException {
		ServerSocketChannel server = ServerSocketChannel.open();
		server.bind(new InetSocketAddress(5678));
		server.configureBlocking(false);
		// OP_ACCEPT, OP_READ, OP_WRITE
		Selector selector = Selector.open();

		server.register(selector, SelectionKey.OP_ACCEPT);
		System.out.println("Server started");

		while (server.isOpen()) {
			selector.select();

			var selectionKeys = selector.selectedKeys();
			var iterator = selectionKeys.iterator();

			while (iterator.hasNext()) {
				var key = iterator.next();
				if (key.isAcceptable()) {
					handleAccept(key, selector);
				} else if (key.isReadable()) {
					handleRead(key, selector);
				}
				iterator.remove();
			}
		}
	}

	private void handleRead(SelectionKey key, Selector selector) throws IOException {
        String nick = "";
		SocketChannel channel = ((SocketChannel) key.channel());
		SocketAddress client = channel.getRemoteAddress();
		int readBytes = channel.read(buffer);
		if (readBytes < 0) {
			channel.close();
			return;
		} else if (readBytes == 0) {
			return;
		}

		buffer.flip();

		StringBuilder sb = new StringBuilder();
		while (buffer.hasRemaining()) {
			sb.append((char) buffer.get());
		}

		buffer.clear();

		// TODO
		// touch [filename] - создание файла
		// mkdir [dirname] - создание директории
		// cd [path] - перемещение по каталогу (.. | ~ )
		// rm [filename | dirname] - удаление файла или папки
		// copy [src] [target] - копирование файла или папки
		// cat [filename] - просмотр содержимого
		// вывод nickname в начале строки

		// NIO
		// NIO telnet server

		if (key.isValid()) {
			String command = sb
					.toString()
					.replace("\n", "")
					.replace("\r", "");

			if ("--help".equals(command)) {
				sendMessage(LS_COMMAND, selector, client);
				sendMessage(MKDIR_COMMAND, selector, client);
				sendMessage(CHANGE_NICKNAME, selector, client);
                sendMessage(TOUCH_COMMAND, selector, client);
                sendMessage(CD_COMMAND, selector, client);
                sendMessage(RM_COMMAND, selector, client);
                sendMessage(COPY_COMMAND, selector, client);
                sendMessage(CAT_COMMAND, selector, client);
			} else if ("ls".equals(command)) {
				sendMessage(getFileList().concat("\n"), selector, client);
			} else if ("mkdir".equals(command)) {
                createDir(selector, channel, client);
            } else if ("touch".equals(command)) {
                createFile(selector, channel, client);
            } else if ("rm".equals(command)) {
                deleteFile(selector, channel, client);
            } else if ("cat".equals(command)) {
                readingFile(selector, channel, client);
            } else if ("copy".equals(command)) {
                copyFile(selector, channel, client);
            } else if (command.startsWith("cd ")) {
                moving(selector, client, command);
            } else if (command.startsWith("nick ")) {
                nick = command.split(" ")[1];
                users.put(channel.getRemoteAddress(), nick);
                sendMessage("new username - " + nick +"\n", selector, client);
            } else if ("exit".equals(command)) {
				System.out.println("Client logged out. IP: " + channel.getRemoteAddress());
				channel.close();
				return;
			}
		}
        nickName(channel, nick);
	}

    private void nickName(SocketChannel channel, String nick) throws IOException {
        if (nick.isEmpty()) {
            nick = users.getOrDefault(channel.getRemoteAddress(), channel.getRemoteAddress().toString());
        }
        String pathString = pathMain.toString().replace("server", "~");
        channel.write(ByteBuffer.wrap(nick.concat(">:").concat(pathString).concat("$ ").getBytes(StandardCharsets.UTF_8)));

    }


    private void moving(Selector selector, SocketAddress client, String command) throws IOException {
        String needPath = command.split(" ")[1];
        Path path1 = Path.of(pathMain.toString(), needPath);
        if ("..".equals(needPath)) {
            path1 = pathMain.getParent();
            if (path1 == null || !path1.toString().startsWith("server")) {
                sendMessage("you are already in the server directory\n", selector, client);
            } else {
                pathMain = path1;
            }
        } else if ("~".equals(needPath)) {
            pathMain = Path.of(ROOT);
        } else {
            if (path1.toFile().exists()) {
                pathMain = path1;
            } else {
                sendMessage("directory doesn't exist\n", selector, client);
            }

        }

    }
    
    private void copyFile(Selector selector, SocketChannel channel, SocketAddress client) throws IOException {
        sendMessage("Enter the path to the file / directory to be copied:\n", selector, client);
        Path path1 = Path.of(returnPathFile(selector, channel));
        sendMessage("Enter destination path for file / directory:\n", selector, client);
        Path path2 = Path.of(returnPathFile(selector, channel));
        if (Files.exists(path1)) {
            Files.copy(path1, path2);
            sendMessage("file / directory copied\n", selector, client);
        } else sendMessage("file / directory not found\n", selector, client);
    }

    private void readingFile(Selector selector, SocketChannel channel, SocketAddress client) throws IOException {
        sendMessage("Enter the name of the file to read:\n", selector, client);
        Path path = Path.of(pathMain + returnPathFile(selector, channel));
        if (Files.exists(path)) {
            byte[] bytes = Files.readAllBytes(path);
            for (byte b : bytes) {
                sendMessage(String.valueOf((char) b), selector, client);
            }
            sendMessage("file read\n", selector, client);
        } else sendMessage("file does not exist\n", selector, client);
    }

    private void deleteFile(Selector selector, SocketChannel channel, SocketAddress client) throws IOException {
        sendMessage("Enter file/directory name:\n", selector, client);
        Path path = Path.of(pathMain + returnPathFile(selector, channel));
        if (Files.exists(path)) {
            Files.delete(path);
            sendMessage("file/directory delete\n", selector, client);
        } else sendMessage("file/directory not found\n", selector, client);
    }

    private void createFile(Selector selector, SocketChannel channel, SocketAddress client) throws IOException {
        sendMessage("Enter file name:\n", selector, client);
        Path path = Path.of(pathMain + returnPathFile(selector, channel));
        if (!Files.exists(path)) {
            Files.createFile(Path.of(String.valueOf(path)));
            sendMessage("file created\n", selector, client);
        } else sendMessage("file exists\n", selector, client);
    }

    private void createDir(Selector selector, SocketChannel channel, SocketAddress client) throws IOException {
        sendMessage("Enter directory name:\n", selector, client);
        Path path = Path.of(pathMain + returnPathFile(selector, channel));
        if (!Files.exists(path)) {
            Files.createDirectory(Path.of(String.valueOf(path)));
            sendMessage("directory created\n", selector, client);
        } else sendMessage("directory exists\n", selector, client);
    }

    private String returnPathFile(Selector selector, SocketChannel channel) throws IOException {
        buffer.clear();
        selector.select();
        int readBytes = channel.read(buffer);
        if (readBytes < 0) {
            channel.close();
        }
        buffer.flip();
        StringBuilder sb = new StringBuilder();
        sb.append("\\");
        while (buffer.hasRemaining()) {
            sb.append((char) buffer.get());
        }
        String directory = sb.toString()
                .replace("\n", "")
                .replace("\r", "");
        buffer.clear();
        return directory;
    }

	private String getFileList() {
		return String.join(" ", new File(pathMain.toString()).list());
	}

	private void sendMessage(String message, Selector selector, SocketAddress client) throws IOException {
		for (SelectionKey key : selector.keys()) {
			if (key.isValid() && key.channel() instanceof SocketChannel) {
				if (((SocketChannel)key.channel()).getRemoteAddress().equals(client)) {
					((SocketChannel)key.channel())
							.write(ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8)));
				}
			}
		}
	}

	private void handleAccept(SelectionKey key, Selector selector) throws IOException {
		SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
		channel.configureBlocking(false);
		System.out.println("Client accepted. IP: " + channel.getRemoteAddress());

		channel.register(selector, SelectionKey.OP_READ, "some attach");
		channel.write(ByteBuffer.wrap("Hello user!\n".getBytes(StandardCharsets.UTF_8)));
		channel.write(ByteBuffer.wrap("Enter --help for support info\n".getBytes(StandardCharsets.UTF_8)));
	}

	public static void main(String[] args) throws IOException {
		new NioTelnetServer();
	}
}
