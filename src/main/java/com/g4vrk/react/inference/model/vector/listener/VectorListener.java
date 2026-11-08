package com.g4vrk.react.inference.model.vector.listener;

import com.g4vrk.react.api.rcp.environment.RCPEnvironment;
import com.g4vrk.react.api.rcp.player.RCPPlayer;
import com.g4vrk.react.api.rcp.player.registry.RCPPlayerRegistry;
import com.g4vrk.react.inference.model.vector.service.AimStreamService;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.UserDisconnectEvent;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

/**
 * Vector Preview receives attacks and rotations at LOW packet priority.
 */
public final class VectorListener extends PacketListenerAbstract {

    private final RCPPlayerRegistry players;
    private final Logger logger;
    private final AimStreamService service;

    public VectorListener(final @NotNull RCPEnvironment environment, final @NotNull AimStreamService service) {
        super(PacketListenerPriority.LOW);
        this.players = environment.players();
        this.logger = environment.logger();
        this.service = service;
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        if (event.getConnectionState() != ConnectionState.PLAY || event.getUser() == null
                || event.getUser().getUUID() == null) {
            return;
        }
        final UUID uuid = event.getUser().getUUID();
        final RCPPlayer player = players.get(uuid);
        try {
            if (player != null) {
                service.onPacket(player, event);
            } else {
                final Object rawPlayer = event.getPlayer();
                service.onPacketBeforeJoin(uuid, rawPlayer instanceof Player bukkitPlayer ? bukkitPlayer : null, event);
            }
        } catch (final RuntimeException exception) {
            logger.debug("Could not process a Vector Preview packet", exception);
        }
    }

    @Override
    public void onUserDisconnect(final UserDisconnectEvent event) {
        if (event.getUser() != null && event.getUser().getUUID() != null) {
            service.removePlayerData(event.getUser().getUUID());
        }
    }

}
