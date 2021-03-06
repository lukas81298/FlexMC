package me.lukas81298.flexmc.io.listener.play;

import me.lukas81298.flexmc.entity.FlexPlayer;
import me.lukas81298.flexmc.io.listener.MessageInboundListener;
import me.lukas81298.flexmc.io.message.play.client.MessageC1AHeldItemChange;
import me.lukas81298.flexmc.io.message.play.server.MessageS3FEntityEquipment;
import me.lukas81298.flexmc.io.netty.ConnectionHandler;
import me.lukas81298.flexmc.util.EventFactory;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;

/**
 * @author lukas
 * @since 07.08.2017
 */
public class HeldItemListener implements MessageInboundListener<MessageC1AHeldItemChange> {

    @Override
    public void handle( ConnectionHandler connectionHandler, MessageC1AHeldItemChange message ) {
        FlexPlayer p = connectionHandler.getPlayer();
        p.handleSetHeldItemSlot( message.getSlot() );
        ItemStack stack = connectionHandler.getPlayer().getInventory().getItem( message.getSlot() );
        EventFactory.call( new PlayerItemHeldEvent( p, p.getHeldItemSlot(), message.getSlot() ) );
        for( FlexPlayer player : connectionHandler.getPlayer().getWorld().getPlayerSet() ) {
            if( !player.equals( connectionHandler.getPlayer() ) ) {
                player.getConnectionHandler().sendMessage( new MessageS3FEntityEquipment( connectionHandler.getPlayer().getEntityId(), 0, stack ) );
            }
        }
    }

}
