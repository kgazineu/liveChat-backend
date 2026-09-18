package com.example.liveChat;

import com.example.liveChat.infra.security.TokenService;
import com.example.liveChat.models.ChannelType;
import com.example.liveChat.models.DirectChannel;
import com.example.liveChat.models.Server;
import com.example.liveChat.models.ServerChannel;
import com.example.liveChat.models.ServerMember;
import com.example.liveChat.models.ServerRole;
import com.example.liveChat.models.User;
import com.example.liveChat.repositories.DirectChannelRepository;
import com.example.liveChat.repositories.ServerChannelRepository;
import com.example.liveChat.repositories.ServerMemberRepository;
import com.example.liveChat.repositories.ServerRepository;
import com.example.liveChat.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebsocketIntegrationTests {
    @LocalServerPort private int port;
    @Autowired private UserRepository users;
    @Autowired private TokenService tokenService;
    @Autowired private DirectChannelRepository directChannels;
    @Autowired private ServerRepository servers;
    @Autowired private ServerMemberRepository members;
    @Autowired private ServerChannelRepository channels;
    @Autowired private SimpUserRegistry userRegistry;
    @Autowired private SimpleBrokerMessageHandler broker;

    @Test
    void anonymousAndInvalidConnectReceiveError() throws Exception {
        for (String credentials : new String[]{"", "Authorization:Bearer invalid\n"}) {
            try (Connection connection = connect()) {
                connection.send("CONNECT\naccept-version:1.2\nhost:localhost\n" + credentials + "\n\0");
                assertThat(connection.next()).startsWith("ERROR\n");
            }
        }
    }

    @Test
    void authenticatedClientCannotSubscribeToBrokerQueue() throws Exception {
        User user = user();
        try (Connection connection = connect()) {
            authenticate(connection, user);
            connection.send("SUBSCRIBE\nid:forbidden\ndestination:/queue/messages\n\n\0");
            assertThat(connection.next()).startsWith("ERROR\n");
        }
    }

    @Test
    void directChannelMessagePersistsAndDeliversToBothParticipants() throws Exception {
        User sender = user();
        User receiver = user();
        DirectChannel directChannel = directChannels.save(new DirectChannel(sender, receiver));
        try (Connection senderConnection = connect(); Connection receiverConnection = connect()) {
            authenticate(senderConnection, sender);
            authenticate(receiverConnection, receiver);
            subscribe(senderConnection, sender);
            subscribe(receiverConnection, receiver);
            senderConnection.send("SEND\ndestination:/app/direct-channels/" + directChannel.getId() + "/messages\ncontent-type:application/json\n\n"
                    + "{\"content\":\"hello\"}\0");
            for (Connection connection : new Connection[]{senderConnection, receiverConnection}) {
                assertThat(connection.next()).startsWith("MESSAGE\n")
                        .contains("\"content\":\"hello\"", "\"authorId\":\"" + sender.getId() + "\"",
                                "\"channelId\":\"" + directChannel.getId() + "\"", "\"id\":");
            }
        }
    }

    @Test
    void serverTextChannelMessageDeliversToEveryMember() throws Exception {
        User owner = user();
        User member = user();
        Server server = servers.save(new Server("Equipe", owner));
        members.save(new ServerMember(server, owner, ServerRole.OWNER));
        members.save(new ServerMember(server, member, ServerRole.MEMBER));
        ServerChannel textChannel = channels.save(new ServerChannel(server, "geral", ChannelType.TEXT, 0));

        try (Connection ownerConnection = connect(); Connection memberConnection = connect()) {
            authenticate(ownerConnection, owner);
            authenticate(memberConnection, member);
            subscribe(ownerConnection, owner);
            subscribe(memberConnection, member);
            ownerConnection.send("SEND\ndestination:/app/servers/" + server.getId() + "/channels/" + textChannel.getId()
                    + "/messages\ncontent-type:application/json\n\n{\"content\":\"mensagem do servidor\"}\0");
            for (Connection connection : new Connection[]{ownerConnection, memberConnection}) {
                assertThat(connection.next()).startsWith("MESSAGE\n")
                        .contains("\"content\":\"mensagem do servidor\"", "\"authorId\":\"" + owner.getId() + "\"",
                                "\"channelId\":\"" + textChannel.getId() + "\"", "\"id\":");
            }
        }
    }

    private void subscribe(Connection connection, User user) {
        connection.send("SUBSCRIBE\nid:messages\ndestination:/user/queue/messages\n\n\0");
        await().atMost(Duration.ofSeconds(5)).until(() -> {
            var connectedUser = userRegistry.getUser(user.getEmail());
            if (connectedUser == null) return false;
            return connectedUser.getSessions().stream().anyMatch(session -> {
                var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
                headers.setDestination("/queue/messages-user" + session.getId());
                return !broker.getSubscriptionRegistry().findSubscriptions(
                        MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders())).isEmpty();
            });
        });
    }

    private void authenticate(Connection connection, User user) throws Exception {
        connection.send("CONNECT\naccept-version:1.2\nhost:localhost\nAuthorization:Bearer "
                + tokenService.generateToken(user).token() + "\n\n\0");
        assertThat(connection.next()).startsWith("CONNECTED\n");
    }

    private User user() {
        return users.save(new User("Test", UUID.randomUUID() + "@example.test", "unused"));
    }

    private Connection connect() throws Exception {
        Connection connection = new Connection();
        connection.socket = connection.client.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create("ws://localhost:" + port + "/ws"), connection)
                .get(5, TimeUnit.SECONDS);
        return connection;
    }

    private static class Connection implements WebSocket.Listener, AutoCloseable {
        private final HttpClient client = HttpClient.newHttpClient();
        private final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        private final StringBuilder buffer = new StringBuilder();
        private WebSocket socket;

        void send(String frame) {
            socket.sendText(frame, true).join();
        }

        String next() throws InterruptedException {
            String frame = frames.poll(5, TimeUnit.SECONDS);
            assertThat(frame).as("STOMP frame received").isNotNull();
            return frame;
        }

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            buffer.append(data);
            drainFrames();
            socket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket socket, ByteBuffer data, boolean last) {
            buffer.append(StandardCharsets.UTF_8.decode(data));
            drainFrames();
            socket.request(1);
            return null;
        }

        private void drainFrames() {
            int end;
            while ((end = buffer.indexOf("\0")) >= 0) {
                frames.add(buffer.substring(0, end).stripLeading());
                buffer.delete(0, end + 1);
            }
        }

        @Override
        public void close() {
            if (socket != null) socket.abort();
            client.close();
        }
    }
}
