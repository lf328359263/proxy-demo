package com.example.echo;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import io.netty.handler.codec.string.StringEncoder;

import java.nio.charset.Charset;

public class Program {

    public static void main(String[] args) throws Exception {

        EchoServer echoServer = new EchoServer("127.0.0.1", 8888); // 启动
        echoServer.start();
    }
}
class EchoServer {
    private final int port;
    private final String ip;

    public EchoServer(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public void start() throws Exception {
        // 创建用于监听accept的线程池
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        // 创建用于处理队列和数据的线程池
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();

            // BACKLOG用于构造服务端套接字ServerSocket对象，标识当服务器请求处理线程全满时
            // ，用于临时存放已完成三次握手的请求的队列的最大长度。如果未设置或所设置的值小于1，Java将使用默认值50。
            bootstrap.option(ChannelOption.SO_BACKLOG, 1024);

            // SO_REUSEADDR允许启动一个监听服务器并捆绑其众所周知端口，
            // 即使以前建立的将此端口用做他们的本地端口的连接仍存在。
            // 这通常是重启监听服务器时出现，若不设置此选项，则bind时将出错。
            // SO_REUSEADDR允许在同一端口上启动同一服务器的多个实例，
            // 只要每个实例捆绑一个不同的本地IP地址即可。对于TCP，
            // 我们根本不可能启动捆绑相同IP地址和相同端口号的多个服务器。
            // SO_REUSEADDR允许单个进程捆绑同一端口到多个套接口上，
            // 只要每个捆绑指定不同的本地IP地址即可。这一般不用于TCP服务器。
            // SO_REUSEADDR允许完全重复的捆绑：当一个IP地址和端口绑定到某个套接口上时，
            // 还允许此IP地址和端口捆绑到另一个套接口上。一般来说，这个特性仅在支持多播的系统上才有，
            // 而且只对UDP套接口而言（TCP不支持多播）
            bootstrap.option(ChannelOption.SO_REUSEADDR, true);

            // Netty4使用对象池，重用缓冲区
            bootstrap.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
            bootstrap.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);

            // 绑定线程池
            bootstrap.group(group, bossGroup);
            // 指定使用的channel
            bootstrap.channel(NioServerSocketChannel.class);
            // 绑定监听端口
            ;
            // 绑定客户端连接时候触发操作
            bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    System.out.println("报告");
                    System.out.println("信息：有一客户端链接到本服务端");
                    System.out.println("IP:" + ch.localAddress().getHostName());
                    System.out.println("Port:" + ch.localAddress().getPort());
                    System.out.println("报告完毕");

                    ch.pipeline().addLast(new StringEncoder(Charset.forName("GBK")));
                    System.out.println("客户端触发操作");
                    ch.pipeline().addLast(new EchoServerHandler()); // 客户端触发操作
                    ch.pipeline().addLast(new ByteArrayEncoder());
                }
            });
            ChannelFuture cf = bootstrap.bind(this.port).sync(); // 服务器异步创建绑定
            System.out.println(EchoServer.class + " 启动正在监听： " + cf.channel().localAddress());
            cf.channel().closeFuture().sync(); // 关闭服务器通道
        } finally {
            // 释放线程池资源
            group.shutdownGracefully().sync();
            bossGroup.shutdownGracefully().sync();
        }
    }
}

class EchoServerHandler extends ChannelInboundHandlerAdapter {

    /**
     * channelAction channel 通道 action 活跃的
     * 当客户端主动链接服务端的链接后，这个通道就是活跃的了。也就是客户端与服务端建立了通信通道并且可以传输数据
     */
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println(ctx.channel().localAddress().toString() + " 通道已激活！");
    }

    /**
     * channelInactive channel 通道 Inactive 不活跃的
     * 当客户端主动断开服务端的链接后，这个通道就是不活跃的。也就是说客户端与服务端的关闭了通信通道并且不可以传输数据
     */
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println(ctx.channel().localAddress().toString() + " 通道不活跃！并且关闭。");
        // 关闭流
        ctx.close();
    }

    /**
     * 功能：读取服务器发送过来的信息
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 第一种：接收字符串时的处理

        ByteBuf buf = (ByteBuf) msg;
        byte[] buffer = new byte[buf.readableBytes()];
        buf.readBytes(buffer, 0, buffer.length);
        String rev = new String(buffer);
        System.out.println("客户端收到服务器数据:" + rev);

        ctx.writeAndFlush(rev);
    }

    /**
     * 功能：读取完毕客户端发送过来的数据之后的操作
     */
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        System.out.println("服务端接收数据完毕..");
        // 第一种方法：写一个空的buf，并刷新写出区域。完成后关闭sock channel连接。
        // ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        // ctx.flush();
        // ctx.flush(); //
        // 第二种方法：在client端关闭channel连接，这样的话，会触发两次channelReadComplete方法。
        // ctx.flush().close().sync(); // 第三种：改成这种写法也可以，但是这中写法，没有第一种方法的好。
    }

    /**
     * 功能：服务端发生异常的操作
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
        System.out.println("异常信息：\r\n");
        cause.printStackTrace();

    }
}