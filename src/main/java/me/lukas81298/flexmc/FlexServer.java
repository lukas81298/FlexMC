package me.lukas81298.flexmc;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.Getter;
import me.lukas81298.flexmc.commands.GamemodeCommand;
import me.lukas81298.flexmc.config.MainConfig;
import me.lukas81298.flexmc.entity.FlexPlayer;
import me.lukas81298.flexmc.inventory.crafting.RecipeManager;
import me.lukas81298.flexmc.inventory.item.Items;
import me.lukas81298.flexmc.io.message.play.server.MessageS1FKeepAlive;
import me.lukas81298.flexmc.io.netty.*;
import me.lukas81298.flexmc.util.crypt.AuthHelper;
import me.lukas81298.flexmc.world.FlexWorld;
import me.lukas81298.flexmc.world.WorldManager;
import me.lukas81298.flexmc.world.block.Blocks;
import org.bukkit.Bukkit;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.SimplePluginManager;

import java.io.File;
import java.security.KeyPair;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author lukas
 * @since 04.08.2017
 */
public class FlexServer{

    @Getter
    private final MainConfig config;
    @Getter
    private final File configFolder;
    private final AtomicBoolean running = new AtomicBoolean( false );

    @Getter private final ListeningExecutorService executorService = MoreExecutors.listeningDecorator( Executors.newCachedThreadPool() );
    @Getter private final ExecutorService packetThreadExecutor = Executors.newSingleThreadExecutor();

    private final ConnectionManager connectionManager = new ConnectionManager();
    private ServerBootstrap serverBootstrap;
    @Getter private FlexWorld world;

    @Getter
    private PlayerManager playerManager = new PlayerManager();

    @Getter
    private KeyPair keyPair;
    @Getter
    private RecipeManager recipeManager = new RecipeManager();

    @Getter
    private SimpleCommandMap commandMap;
    @Getter
    private SimplePluginManager pluginManager;
    @Getter
    private final WorldManager worldManager = new WorldManager();
    @Getter
    private FlexServerImpl server;

    public FlexServer( MainConfig config, File configFolder ) {
        this.config = config;
        this.configFolder = configFolder;
    }

    public ListenableFuture<Void> start() {
        Runtime.getRuntime().addShutdownHook( new Thread( this::stop ) );
        return executorService.submit( new Callable<Void>() {
            public Void call() throws Exception {

                if( config.isVerifyUsers() ) {
                    System.out.println( "Generating keypair with 1024 bit length" );
                    long start = System.currentTimeMillis();
                    keyPair = AuthHelper.generateServerKeyPair();
                    System.out.println( "Took " + ( System.currentTimeMillis() - start ) + "ms" );
                } else {
                    System.out.println( "Server will not verify names. Use this option wisely, everyone could log in with any account" );
                }


                NioEventLoopGroup acceptorGroup = new NioEventLoopGroup( config.getAcceptorThreads() );
                NioEventLoopGroup handlerGroup = new NioEventLoopGroup( config.getHandlerThreads() );

                System.out.println( "Starting socket server..." );
                serverBootstrap = new ServerBootstrap();

                serverBootstrap.group( acceptorGroup, handlerGroup )
                        .channel( NioServerSocketChannel.class )
                        .childHandler( new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel( SocketChannel socketChannel ) throws Exception {
                                socketChannel.config().setOption( ChannelOption.TCP_NODELAY, true );
                                ChannelPipeline pipeline = socketChannel.pipeline();
                                pipeline.addLast( "splitter", new MessageSplitter() );
                                pipeline.addLast( "decoder", new MessageDecoder( connectionManager ) );
                                pipeline.addLast( "prepender", new MessagePrepender() );
                                pipeline.addLast( "encoder", new MessageEncoder( connectionManager ) );
                                pipeline.addLast( "connectionHandler", new ConnectionHandler( connectionManager ) );
                            }
                        } ).localAddress( config.getServerAddress(), config.getServerPort() ).bind().syncUninterruptibly(); // wait until started



                server = new FlexServerImpl( FlexServer.this );
                Bukkit.setServer( server );
                commandMap = new SimpleCommandMap( server );
                commandMap.register( "flex", new GamemodeCommand() );

                pluginManager = new SimplePluginManager( server, commandMap );
                System.out.println( "Loading plugins..." );
                File plugins = new File( "plugins" );
                if( !plugins.exists() ) {
                    plugins.mkdir();
                }
                pluginManager.loadPlugin( plugins );
                System.out.println( "Enabling plugins" );
                for ( Plugin plugin : pluginManager.getPlugins() ) {
                    pluginManager.enablePlugin( plugin );
                }
                System.out.println( "Loading blocks" );
                Blocks.initBlocks(); // register all blocks
                System.out.println( "Loading items" );
                Items.initItems();

                running.set( true );
                System.out.println( "Server started! Generating worlds..." );
                world = new FlexWorld( "world" );

                executorService.execute( new Runnable() {
                    @Override
                    public void run() {
                        while( running.get() ) {
                            for ( FlexPlayer player : playerManager.getOnlinePlayers() ) {
                                if( ( System.currentTimeMillis() - player.getLastKeepAlive() ) > 30 * 1000L ) {
                                    player.kickPlayer( "Timed out (server)" );
                                }
                            }
                            try {
                                Thread.sleep( 1000L );
                            } catch ( InterruptedException e ) {
                                e.printStackTrace();
                            }
                        }
                    }
                } );

                executorService.execute( new Runnable() {
                    @Override
                    public void run() {
                        while ( running.get() ) {
                            int i = (int) ( Math.random() * Integer.MAX_VALUE );
                            for ( FlexPlayer player : playerManager.getOnlinePlayers() ) {
                                player.getConnectionHandler().sendMessage( new MessageS1FKeepAlive( i ) );
                            }
                            try {
                                Thread.sleep( 1000L * 10 );
                            } catch ( InterruptedException e ) {
                                e.printStackTrace();
                            }
                        }
                    }
                } );
                return null;
            }
        } );
    }

    public boolean isRunning() {
        return this.running.get();
    }

    public void stop() {
        System.out.println( "Disabling plugins" );
        this.pluginManager.disablePlugins();
        if ( this.running.compareAndSet( false, true ) ) {
            System.out.println( "Stopping server..." );
            for( FlexPlayer player : playerManager.getOnlinePlayers() ) {
                player.kickPlayer( "Server stopped!" );
            }
            System.exit( 0 );
        } else {
            System.out.println( "Attempting to shut down server but already stopped or currently shutting down" );
        }
    }


}
