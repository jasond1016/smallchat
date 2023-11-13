import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author atdt
 */
public class SmallChat {
    public static final int PORT = 7712;
    Map<SocketChannel, Client> clients;
    private ServerSocketChannel serverSocketChannel;
    private Selector selector;

    static class Client {
        String nickName;

        public Client(String nickName) {
            this.nickName = nickName;
        }
    }

    private void start(int port) throws IOException {
        clients = new HashMap<>();
        selector = Selector.open();
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.bind(new InetSocketAddress(port));
        serverSocketChannel.register(selector, serverSocketChannel.validOps());

        System.out.println("SmallChat server started on port: " + port);

        while (true) {
            selector.select();
            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
            while (iterator.hasNext()) {
                SelectionKey selectionKey = iterator.next();
                iterator.remove();

                if (selectionKey.isAcceptable()) {
                    acceptNewConnection();
                } else if (selectionKey.isReadable()) {
                    processClientMessage((SocketChannel) selectionKey.channel());
                }
            }
        }
    }

    private void acceptNewConnection() throws IOException {
        SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_READ);

        Client newClient = new Client("client" + ThreadLocalRandom.current().nextInt(1000));
        clients.put(socketChannel, newClient);
        System.out.println("New client connected: " + newClient.nickName);

        String greetingMessage = """
                Welcome to SmallChat, %s!
                Use /nick to set your nickname.
                """;
        socketChannel.write(ByteBuffer.wrap(greetingMessage.formatted(newClient.nickName).getBytes()));
    }

    private void processClientMessage(SocketChannel socketChannel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead = socketChannel.read(buffer);
        if (bytesRead == -1) {
            disconnectClient(socketChannel);
            return;
        }

        buffer.flip();
        byte[] bytes = new byte[buffer.limit()];
        buffer.get(bytes);

        String message = new String(bytes);
        if (!message.isEmpty()) {
            handleMessage(message, socketChannel);
        }
    }

    private void disconnectClient(SocketChannel socketChannel) throws IOException {
        Client client = this.clients.get(socketChannel);
        clients.remove(socketChannel);
        socketChannel.close();
        System.out.println("Client disconnected: " + client.nickName);
    }

    private void handleMessage(String message, SocketChannel sender) throws IOException {
        Client SenderClient = this.clients.get(sender);
        System.out.print("Message received from " + SenderClient.nickName + ": " + message);

        if (message.startsWith("/nick ")) {
            String newName = message.substring(6).trim().replace("\r\n", "").replace("\n", "");
            String formattedMessage = "Client " + SenderClient.nickName + " changed nickname to " + newName + "\n";
            System.out.print(formattedMessage);
            SenderClient.nickName = newName;
            broadcastMessage(formattedMessage, null);
        } else {
            String formattedMessage = SenderClient.nickName + "> " + message;
            broadcastMessage(formattedMessage, sender);
        }
    }

    private void broadcastMessage(String message, SocketChannel sender) throws IOException {
        for (SocketChannel socketChannel : this.clients.keySet()) {
            if (!socketChannel.equals(sender)) {
                socketChannel.write(ByteBuffer.wrap(message.getBytes()));
            }
        }
    }

    public static void main(String[] args) throws IOException {
        SmallChat smallChat = new SmallChat();
        smallChat.start(PORT);
    }
}
