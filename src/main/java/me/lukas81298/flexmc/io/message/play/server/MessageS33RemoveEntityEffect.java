package me.lukas81298.flexmc.io.message.play.server;

import io.netty.buffer.ByteBuf;
import lombok.*;
import me.lukas81298.flexmc.io.message.Message;

import java.io.IOException;

/**
 * @author lukas
 * @since 07.08.2017
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode( callSuper = false )
public class MessageS33RemoveEntityEffect extends Message {

    private int entityId;
    private byte effectId;

    @Override
    public void read( ByteBuf buf ) throws IOException {

    }

    @Override
    public void write( ByteBuf buf ) throws IOException {
        writeVarInt( entityId, buf );
        buf.writeByte( effectId );
    }
}
