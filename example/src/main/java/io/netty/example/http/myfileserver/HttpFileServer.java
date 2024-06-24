package io.netty.example.http.myfileserver;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.stream.ChunkedWriteHandler;

/**
 * @author Runner
 * @version 1.0
 * @date 2024/6/17 10:50
 * @description
 */
public class HttpFileServer {
    private static final String DEFAULT_URL = "/example/src/main/java/io/";

    public void run(final int port, final String url) throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch)
                                throws Exception {
                            // 请求消息解码器
                            ch.pipeline().addLast("http-decoder", new HttpRequestDecoder());
                            // 目的是将多个消息转换为单一的FullRequest或者FullResponse对象,原因是HTTP解码器在每个HTTP消息中会生成多个消息对象。
                            // 1、HttpRequest/HttpResponse;
                            // 2、HttpContent;
                            // 3、LastHttpContent。
                            ch.pipeline().addLast("http-aggregator", new HttpObjectAggregator(65536));
                            // 响应编码器
                            ch.pipeline().addLast("http-encoder", new HttpResponseEncoder());
                            // 支持异步发送大的码流（例如大的文件传输），但不占用过多的内存，防止发生Java内存溢出错误。
                            ch.pipeline().addLast("http-chunked", new ChunkedWriteHandler());
                            // 业务逻辑
                            ch.pipeline().addLast("fileServerHandler", new HttpFileServerHandler(url));
                        }
                    });
            ChannelFuture future = b.bind("127.0.0.1", port).sync();
            System.out.println("HTTP文件目录服务器启动，网址是 : " + "http://127.0.0.1:"
                    + port + url);
            future.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws Exception {
        int port = 8080;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
        String url = DEFAULT_URL;
        if (args.length > 1)
            url = args[1];
        new HttpFileServer().run(port, url);
    }
}
