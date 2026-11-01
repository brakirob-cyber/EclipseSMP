package dev.eclipse.smp;

import org.bukkit.Color;

public enum Ability {
    PHANTOM("phantom", "Phantom", "Mobility", "#8fd3ff", 35,
            "Phantom Step", "Teleport behind a targeted enemy within 7 blocks.",
            "Ghost Trail", "Every 6th successful hit has a 20% chance to trigger a ghost strike, briefly making you transparent and leaving a spectral afterimage."),
    CROSSCUT("crosscut", "Crosscut", "Offense", "#ff4d4d", 12,
            "Double Slash", "Perform two quick energy slashes, each dealing 1 heart of damage.",
            "Blood Combo", "Every third consecutive hit on the same enemy creates a red slash effect and deals 0.5 extra hearts."),
    COUNTER("counter", "Counter", "Defense", "#4da6ff", 20,
            "Counter Stance", "Block the next incoming hit during a 0.8 second window and knock back the attacker.",
            "Retaliation", "15% chance when hit to release a small knockback shockwave."),
    SINGULARITY("singularity", "Singularity", "Control", "#a05cff", 40,
            "Black Hole", "Create a gravity orb that pulls nearby enemies towards its centre for 2 seconds.",
            "Gravity Well", "Every 5th successful hit slightly pulls the enemy towards you."),
    CHAINREACTION("chainreaction", "Chain Reaction", "Electric", "#ffe44d", 18,
            "Chain Lightning", "Fire an electric chain that damages an enemy and can jump to one nearby enemy.",
            "Static", "Every 5th successful hit releases an electric shock dealing 0.5 extra hearts."),
    OVERLOAD("overload", "Overload", "Explosion", "#ff8c1a", 30,
            "Overload", "Charge for 1 second and release an explosion that damages and knocks back nearby enemies.",
            "Berserker", "Deal 4% extra melee damage while below 5 hearts."),
    AFTERIMAGE("afterimage", "Afterimage", "Trickery", "#ff8de1", 30,
            "Afterimage", "Create two fake copies of yourself that run in different directions.",
            "Phase Shift", "12% chance to dodge an incoming hit, briefly turning into a ghost. 12 second internal cooldown."),
    REWIND("rewind", "Rewind", "Time", "#5dff7a", 45,
            "Rewind", "Save your position and return to it within 4 seconds by activating the ability again.",
            "Second Wind", "When below 4 hearts, receive a short speed burst once per combat encounter."),
    SOULCHAINS("soulchains", "Soul Chains", "Control", "#3fd0c9", 35,
            "Soul Chains", "Connect to an enemy and restrict their movement radius for 2 seconds.",
            "Leech", "Every 8th successful hit restores half a heart."),
    PHOENIX("phoenix", "Phoenix", "Survival", "#ff5a1f", 45,
            "Phoenix Burst", "Launch yourself upwards with an explosion of fire particles and temporary fall damage protection.",
            "Rebirth", "When you drop below 4 hearts, release a burst that pushes nearby enemies away. 90 second cooldown.");

    public final String id;
    public final String display;
    public final String category;
    public final String color;
    public final double defaultCooldown;
    public final String activeName;
    public final String activeDesc;
    public final String passiveName;
    public final String passiveDesc;

    Ability(String id, String display, String category, String color, double defaultCooldown,
            String activeName, String activeDesc, String passiveName, String passiveDesc) {
        this.id = id;
        this.display = display;
        this.category = category;
        this.color = color;
        this.defaultCooldown = defaultCooldown;
        this.activeName = activeName;
        this.activeDesc = activeDesc;
        this.passiveName = passiveName;
        this.passiveDesc = passiveDesc;
    }

    public String tag() {
        return "<color:" + color + ">";
    }

    public Color bukkitColor() {
        return Color.fromRGB(Integer.parseInt(color.substring(1), 16));
    }

    public static Ability byId(String s) {
        if (s == null) return null;
        for (Ability a : values()) {
            if (a.id.equalsIgnoreCase(s)) return a;
        }
        return null;
    }
}
