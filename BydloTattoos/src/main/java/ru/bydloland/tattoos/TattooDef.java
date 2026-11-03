package ru.bydloland.tattoos;

import java.awt.image.BufferedImage;
import java.util.List;

public record TattooDef(String id, String name, BufferedImage stamp, List<TattooSlot> slots) {
}
