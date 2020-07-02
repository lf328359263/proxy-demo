package com.example;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;


public class ImpalaProxyServer {

    private static final Logger logger = LoggerFactory.getLogger(ImpalaProxyServer.class);

    public static void main(String[] args) {
        Vertx.vertx().deployVerticle(new ImpalaProxyServerVerticle());
    }

    public static class ImpalaProxyServerVerticle extends AbstractVerticle {
        private final int sourcePort = 21050;
        private final String impalaHost = "10.8.28.83";
        @Override
        public void start() throws Exception {
            NetServer netServer = vertx.createNetServer();//创建代理服务器
            NetClient netClient = vertx.createNetClient();//创建连接impala客户端
            int targetPort = 21050;
            netServer.connectHandler(socket -> netClient.connect(sourcePort, impalaHost, result -> {
                //响应来自客户端的连接请求，成功之后，在建立一个与目标impala服务器的连接
                if (result.succeeded()) {
                    //与目标impala服务器成功连接连接之后，创造一个ImpalaProxyConnection对象,并执行代理方法
                    new ImpalaProxyConnection(socket, result.result()).proxy();
                } else {
                    logger.error(result.cause().getMessage(), result.cause());
                    socket.close();
                }
            })).listen(targetPort, listenResult -> {//代理服务器的监听端口
                if (listenResult.succeeded()) {
                    //成功启动代理服务器
                    logger.info("Impala proxy server start up.");
                } else {
                    //启动代理服务器失败
                    logger.error("Impala proxy exit. because: " + listenResult.cause().getMessage(), listenResult.cause());
                    System.exit(1);
                }
            });
        }
    }

    public static class ImpalaProxyConnection {
        private final NetSocket clientSocket;
        private final NetSocket serverSocket;

        public ImpalaProxyConnection(NetSocket clientSocket, NetSocket serverSocket) {
            this.clientSocket = clientSocket;
            this.serverSocket = serverSocket;
        }

        private void proxy() {
            //当代理与impala服务器连接关闭时，关闭client与代理的连接
            serverSocket.closeHandler(v -> clientSocket.close());
            //反之亦然
            clientSocket.closeHandler(v -> serverSocket.close());
            //不管那端的连接出现异常时，关闭两端的连接
            serverSocket.exceptionHandler(e -> {
                logger.error(e.getMessage(), e);
                close();
            });
            clientSocket.exceptionHandler(e -> {
                logger.error(e.getMessage(), e);
                close();
            });
            AtomicInteger clientCount = new AtomicInteger(0);
            AtomicInteger serverCount = new AtomicInteger(0);
            //当收到来自客户端的数据包时，转发给impala目标服务器
            clientSocket.handler(buffer -> {
                System.out.println(String.format("⬇################## client: %s ##################⬇", clientCount.get()));
                System.out.println(buffer.length() + " --> |" + buffer.toString(StandardCharsets.ISO_8859_1) + "|");
                System.out.println(String.format("⬆################## client: %s ##################⬆", clientCount.get()));
                clientCount.incrementAndGet();
                serverSocket.write(buffer);
            });
            //当收到来自impala目标服务器的数据包时，转发给客户端
            serverSocket.handler(buffer -> {
                System.out.println(String.format("⬇################## server: %s ##################⬇", serverCount.get()));
                System.out.println(buffer.length() + " --> |" + buffer.toString(StandardCharsets.ISO_8859_1) + "|");
                System.out.println(String.format("⬆################## server: %s ##################⬆", serverCount.get()));
                serverCount.incrementAndGet();
                clientSocket.write(buffer);
            });
        }

        private void close() {
            clientSocket.close();
            serverSocket.close();
        }
    }
}
