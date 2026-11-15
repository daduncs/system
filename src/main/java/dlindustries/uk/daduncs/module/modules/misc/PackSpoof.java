/*
 * This file is part of System Client (https://github.com/daduncs/system)
 * Website: https://download.daduncs.cloud/
 *
 * Copyright (c) 2024 - 2026 DLindustries and project contributors.
 *
 * System Client is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * System Client is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with System Client. If not, see <https://www.gnu.org/licenses/>.
 */

package dlindustries.uk.daduncs.module.modules.misc;

import dlindustries.uk.daduncs.event.events.PacketReceiveListener;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.utils.EncryptedString;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;

public class PackSpoof extends Module implements PacketReceiveListener {
    public PackSpoof() {
        super(EncryptedString.of("Pack Spoof"), EncryptedString.of("Ignores custom resource packs"), -1, Category.MISC);
    }

    @Override
    public void onEnable() {
        eventManager.add(PacketReceiveListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(PacketReceiveListener.class, this);
        super.onDisable();
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if(mc.getNetworkHandler() != null) {
            Packet<?> packet = event.packet;
            if (packet instanceof ResourcePackSendS2CPacket packPacket) {
                event.cancel();

                mc.getNetworkHandler().sendPacket(new ResourcePackStatusC2SPacket(packPacket.id(), ResourcePackStatusC2SPacket.Status.ACCEPTED));
                mc.getNetworkHandler().sendPacket(new ResourcePackStatusC2SPacket(packPacket.id(), ResourcePackStatusC2SPacket.Status.DOWNLOADED));
                mc.getNetworkHandler().sendPacket(new ResourcePackStatusC2SPacket(packPacket.id(), ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED));
            }
        }
    }
}
