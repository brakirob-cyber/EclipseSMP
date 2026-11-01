package dev.eclipse.smp;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class PlayerData {
    public int essence = 0;
    public final Set<Ability> unlocked = EnumSet.noneOf(Ability.class);
    public final List<Ability> equipped = new ArrayList<>();
}
