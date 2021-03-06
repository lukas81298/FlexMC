package me.lukas81298.flexmc.io.message.play.server;

import io.netty.buffer.ByteBuf;
import lombok.*;
import me.lukas81298.flexmc.io.message.Message;

import java.io.IOException;

/**
 * @author lukas
 * @since 06.08.2017
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode( callSuper = false )
public class MessageS26EntityRelMove extends Message {

    private int entityId;
    private short deltaX, deltaY, deltaZ;
    private boolean onGround;

    @Override
    public void read( ByteBuf buf ) throws IOException {
        entityId = readVarInt( buf );
        deltaX = buf.readShort();
        deltaY = buf.readShort();
        deltaZ = buf.readShort();
        onGround = buf.readBoolean();
    }

    @Override
    public void write( ByteBuf buf ) throws IOException {
        writeVarInt( entityId, buf );
        buf.writeShort( deltaX );
        buf.writeShort( deltaY );
        buf.writeShort( deltaZ );
        buf.writeBoolean( onGround );
    }
}
