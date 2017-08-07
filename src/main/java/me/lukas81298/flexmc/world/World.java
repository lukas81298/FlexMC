package me.lukas81298.flexmc.world;

import gnu.trove.map.TByteObjectMap;
import gnu.trove.map.hash.TByteObjectHashMap;
import gnu.trove.procedure.TObjectProcedure;
import lombok.Getter;
import me.lukas81298.flexmc.Flex;
import me.lukas81298.flexmc.entity.Entity;
import me.lukas81298.flexmc.entity.EntityObject;
import me.lukas81298.flexmc.entity.Item;
import me.lukas81298.flexmc.entity.Player;
import me.lukas81298.flexmc.inventory.ItemStack;
import me.lukas81298.flexmc.io.message.play.server.MessageS47TimeUpdate;
import me.lukas81298.flexmc.io.message.play.server.*;
import me.lukas81298.flexmc.io.netty.ConnectionHandler;
import me.lukas81298.flexmc.util.Difficulty;
import me.lukas81298.flexmc.util.Location;
import me.lukas81298.flexmc.util.Vector3i;
import me.lukas81298.flexmc.world.generator.ChunkGenerator;
import me.lukas81298.flexmc.world.generator.FancyWorldGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.IntUnaryOperator;

/**
 * @author lukas
 * @since 06.08.2017
 */
public class World {

    private static final int maxWorldTicks = 180000;

    @Getter
    private final String name;

    private final ReadWriteLock chunkLock = new ReentrantReadWriteLock();
    private final TByteObjectMap<TByteObjectMap<ChunkColumn>> columns = new TByteObjectHashMap<>();

    private final Set<Entity> entities = ConcurrentHashMap.newKeySet();
    @Getter
    private final Set<Player> players = ConcurrentHashMap.newKeySet();

    private final AtomicInteger time = new AtomicInteger( 1000 );
    private final AtomicInteger worldAge = new AtomicInteger( 0 );
    private final AtomicInteger entityIdCounter = new AtomicInteger( 0 );

    private final ChunkGenerator generator = new FancyWorldGenerator();

    @Getter
    private Difficulty difficulty = Difficulty.PEACEFUL;
    @Getter
    private final Dimension dimension = Dimension.OVER_WORLD;

    private byte timeCounter = 0;

    public World( String name ) {
        this.name = name;
        System.out.println( "Generating chunks for " + name );
        for ( int x = -8; x < 8; x++ ) {
            for ( int z = -8; z < 8; z++ ) {
                this.generateColumn( x, z );
            }
        }
        System.out.println( "Done" );
        Flex.getServer().getExecutorService().execute( new Runnable() {
            @Override
            public void run() {
                while ( Flex.getServer().isRunning() ) {
                    long start = System.currentTimeMillis();
                    tickEntities();
                    long diff = System.currentTimeMillis() - start;
                    try {
                        Thread.sleep( Math.max( 0, 50 - diff ) );
                    } catch ( InterruptedException e ) {
                        e.printStackTrace();
                    }

                }
            }
        } );
        Flex.getServer().getExecutorService().execute( new Runnable() {
            @Override
            public void run() {
                while ( Flex.getServer().isRunning() ) {
                    long start = System.currentTimeMillis();
                    tick();
                    long diff = System.currentTimeMillis() - start;
                    try {
                        Thread.sleep( Math.max( 0, 50 - diff ) );
                    } catch ( InterruptedException e ) {
                        e.printStackTrace();
                    }

                }
            }
        } );
        Flex.getServer().getExecutorService().execute( new Runnable() {
            @Override
            public void run() {
                while ( Flex.getServer().isRunning() ) {
                    long start = System.currentTimeMillis();
                    tickPlayers();
                    long diff = System.currentTimeMillis() - start;
                    try {
                        Thread.sleep( Math.max( 0, 50 - diff ) );
                    } catch ( InterruptedException e ) {
                        e.printStackTrace();
                    }

                }
            }
        } );
    }

    private void tickPlayers() {
        for( Player player : this.players ) {
            player.tick();
            if( !player.isAlive() ) {
                // tja er ist tot, aber eigentlich müssen wir hier nichts machen
            }
        }
    }

    private void tickEntities() {
        for( Entity entity : this.entities ) {
            entity.tick();
            if( !entity.isAlive() && !( entity instanceof Player ) ) { // repawning is handled differently for players
                this.removeEntity( entity );
            }
        }
    }

    private void tick() {
        int time = this.time.updateAndGet( new IntUnaryOperator() {
            @Override
            public int applyAsInt( int operand ) {
                return operand < maxWorldTicks ? ( operand + 1 ) : 0;
            }
        } );
        int age = worldAge.incrementAndGet();
        if( timeCounter == 20 ) {
            timeCounter = 0;
            for ( Player player : players ) {
                player.getConnectionHandler().sendMessage( new MessageS47TimeUpdate( age, time ) );
            }
        } else {
            timeCounter++;
        }
    }

    public void spawnItem( Location location, ItemStack itemStack ) {

        Item item = new Item( nextEntityId(), location, this );
        item.setItemStack( itemStack );
        this.addEntity( item, false );
    }

    public void addEntity( Entity entity, boolean changeWorld ) {
        if( changeWorld ) {
            entity.changeWorld( this, nextEntityId() );
        }
        if ( entity instanceof Player ) {
            Player player = (Player) entity;
            for ( Player t : players ) {
                sendToPlayer( t, player.getConnectionHandler() );
                sendToPlayer( player, t.getConnectionHandler() );
            }
            synchronized ( this ) {
                this.entities.add( entity );
                this.players.add( player );
            }
        } else {
            this.entities.add( entity );
            if( entity instanceof EntityObject ) {
                byte t = ((EntityObject) entity).getObjectType();
                Location l = entity.getLocation();
                for( Player player : players ) {
                    player.getConnectionHandler().sendMessage( new MessageS00SpawnObject( entity.getEntityId(), UUID.randomUUID(), t, l.x(), l.y(), l.z(), 3F, 3F ) );
                    player.getConnectionHandler().sendMessage( new MessageS3CEntityMetaData( entity.getEntityId(), entity.getMetaData() ) );
                }
            }
        }
    }

    private void sendToPlayer( Player player, ConnectionHandler connectionHandler ) {
        Location playerLocation = player.getLocation();
        connectionHandler.sendMessage( new MessageS05SpawnPlayer( player.getEntityId(), player.getUuid(), playerLocation.x(), playerLocation.y(), playerLocation.z(),
                playerLocation.yaw(), playerLocation.pitch(), player.getMetaData() ) );
    }

    public void removeEntity( Entity entity ) {
        if ( entity instanceof Player ) {
            synchronized ( this ) {
                this.entities.remove( entity );
                this.players.remove( entity );
            }
        } else {
            this.entities.remove( entity );
        }
        for( Player player : this.players ) {
            player.getConnectionHandler().sendMessage( new MessageS32DestroyEntities( entity.getEntityId() ) );
        }
    }

    private int nextEntityId() {
        return this.entityIdCounter.updateAndGet( new IntUnaryOperator() {
            @Override
            public int applyAsInt( int operand ) {
                return operand < Integer.MAX_VALUE ? ( operand + 1 ) : 0; // just in case :P
            }
        } );
    }

    public Location getSpawnLocation() {
        return new Location( 0, 70, 0 );
    }

    public ChunkColumn getChunkAt( int x, int z ) {
        this.chunkLock.readLock().lock();
        try {
            int i = x / 16;
            int j = z / 16;
            TByteObjectMap<ChunkColumn> map = this.columns.get( (byte) i );
            if ( map == null ) {
                return null;
            }
            return map.get( (byte) j );
        } finally {
            this.chunkLock.readLock().unlock();
        }
    }

    private void generateColumn( int x, int z ) {
        this.chunkLock.writeLock().lock();
        try {
            ChunkColumn chunkColumnColumn = new ChunkColumn( x, z );
            for ( int i = 0; i < chunkColumnColumn.getSections().length; i++ ) {
                ChunkSection section = new ChunkSection();
                chunkColumnColumn.getSections()[i] = section;
            }
            this.generator.generate( chunkColumnColumn );
            TByteObjectMap<ChunkColumn> c = this.columns.get( (byte) x );
            if ( c == null ) {
                this.columns.put( (byte) x, c = new TByteObjectHashMap<>() );
            }
            c.put( (byte) z, chunkColumnColumn );
        } finally {
            this.chunkLock.writeLock().unlock();
        }
    }

    public List<ChunkColumn> getColumns() {
        List<ChunkColumn> list = new ArrayList<>();
        this.chunkLock.readLock().lock();
        try {
            this.columns.forEachValue( new TObjectProcedure<TByteObjectMap<ChunkColumn>>() {
                @Override
                public boolean execute( TByteObjectMap<ChunkColumn> value ) {
                    list.addAll( value.valueCollection() );
                    return true;
                }
            } );
        } finally {
            this.chunkLock.readLock().unlock();
        }
        return list;
    }

    public BlockState getBlockAt( Vector3i position ) {
        ChunkColumn column = this.getChunkAt( position.getX(), position.getZ() );
        int sectionIndex = position.getY() / 16;
        ChunkSection section = column.getSections()[sectionIndex];
        int i = section.getBlock( fixIndex( position.getX() % 16 ), position.getY() % 16, fixIndex( position.getZ() % 16 ) );
        int type = i >> 4;
        int data = i & 15;
        return new BlockState( type, data );
    }

    public void setBlock( Vector3i position, BlockState state ) {
        ChunkColumn column = this.getChunkAt( position.getX(), position.getZ() );
        int sectionIndex = position.getY() / 16;
        ChunkSection section = column.getSections()[sectionIndex];
        section.setBlock( fixIndex( position.getX() % 16 ), position.getY() % 16, fixIndex( position.getZ() % 16 ), state.getId(), state.getData() );
        for( Player player : this.players ) {
            player.getConnectionHandler().sendMessage( new MessageS0BBlockChange( position, state ) );
        }
    }

    private int fixIndex( int z ) {
        if( z >= 0 ) {
            return z;
        }
        return 16 + z;
    }
}
